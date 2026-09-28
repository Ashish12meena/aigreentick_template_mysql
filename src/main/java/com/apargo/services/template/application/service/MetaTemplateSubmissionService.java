package com.apargo.services.template.application.service;

import org.springframework.stereotype.Service;

import com.apargo.services.template.application.audit.TemplateAuditEvents;
import com.apargo.services.template.application.audit.TemplateAuditEvents.SubmissionOutcome;
import com.apargo.services.template.application.audit.TemplateAuditSnapshot;
import com.apargo.services.template.application.dto.TenantScope;
import com.apargo.services.template.application.dto.client.AccessTokenIdentifier;
import com.apargo.services.template.application.dto.client.FacebookApiResponse;
import com.apargo.services.template.application.dto.result.TemplateResult;
import com.apargo.services.template.application.mapper.WhatsappTemplateMapper;
import com.apargo.services.template.application.port.out.FacebookTemplatePort;
import com.apargo.services.template.application.port.out.WabaCredentialPort;
import com.apargo.services.template.common.constant.MetaGraph;
import com.apargo.services.template.common.constant.TemplateConstants;
import com.apargo.services.template.common.exception.WhatsappCredentialsNotFoundException;
import com.apargo.services.template.domain.model.WhatsappTemplate;
import com.apargo.services.template.domain.service.TemplateCommandService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Submits an already-SUBMITTED template to Meta and records the outcome.
 * Used by both CreateTemplateUseCaseImpl and SubmitDraftToMetaUseCaseImpl.
 *
 * <h2>No transaction is held across the Meta call</h2>
 *
 * The caller commits the row as SUBMITTED <em>before</em> calling this, and
 * every write here is its own short transaction (TemplateCommandService).
 * Previously the whole create - insert, Meta call, result - was one
 * transaction, which had two consequences:
 * <ul>
 *   <li>any exception after Meta accepted rolled the row back, leaving a
 *       template on Meta that this service had no record of (and whose name
 *       could not be reused);</li>
 *   <li>a DB connection was held for the full Meta round-trip (up to the
 *       30s read timeout), so a slow Meta could exhaust the pool.</li>
 * </ul>
 *
 * <h2>Outcomes</h2>
 * <ul>
 *   <li><b>Meta accepted</b> - status becomes Meta's (PENDING/APPROVED/...).</li>
 *   <li><b>Meta rejected (4xx, or an error in a 2xx body)</b> - FAILED, with
 *       Meta's user-facing message in rejectionReason and its raw JSON in
 *       metaResponse. FAILED is not live, so the name can be reused.</li>
 *   <li><b>Outcome unknown</b> (timeout, 5xx, or the result could not be
 *       saved) - the row stays SUBMITTED and
 *       {@link SubmittedTemplateReconciler} settles it against Meta.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MetaTemplateSubmissionService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TemplateCommandService commandService;
    private final WhatsappTemplateMapper templateMapper;
    private final WabaCredentialPort wabaCredentialPort;
    private final FacebookTemplatePort ftp;
    private final TemplateAuditEvents audit;

    /**
     * @param template a template already committed with status SUBMITTED
     * @param payload  serialized JSON body for the Meta API
     * @param wabaId   WhatsApp Business Account ID
     * @param before   the template as it was before this submit (DRAFT when a
     *                 draft is submitted; SUBMITTED on create), for the
     *                 audit event's old values
     */
    public TemplateResult submitToMeta(WhatsappTemplate template, String payload, String wabaId,
                                       TemplateAuditSnapshot before) {

        // Step 1: Resolve access token. Nothing has been sent to Meta yet, so a
        // failure here is definitive: mark FAILED (frees the name) and rethrow
        // so the caller still gets the upstream error status.
        AccessTokenIdentifier credentials;
        try {
            credentials = wabaCredentialPort.getWhatsappAccountWabaAccessToken(wabaId, TenantScope.of(template));
            if (credentials == null || credentials.getAccessToken() == null
                    || credentials.getAccessToken().isBlank()) {
                throw new WhatsappCredentialsNotFoundException("Access token not found for wabaId: " + wabaId);
            }
        } catch (RuntimeException ex) {
            safeMarkFailed(template, TemplateConstants.RejectionReasons.CREDENTIALS_UNAVAILABLE, null);
            audit.templateSubmitFailed(before, template, ex);
            throw ex;
        }

        // Step 2: Call Meta. No transaction and no DB connection held here.
        FacebookApiResponse<JsonNode> fbResponse = ftp.createTemplate(payload, wabaId, credentials.getAccessToken());

        // Step 3: Record the outcome.
        if (fbResponse == null) {
            return outcomeUnknown(template, before, "Empty response from Facebook adapter");
        }
        if (fbResponse.isSuccess()) {
            return handleSuccessBody(template, before, fbResponse.getData());
        }
        if (fbResponse.isClientError()) {
            return rejected(template, before, fbResponse.getErrorMessage());
        }
        // 5xx or no response at all: Meta may have created it.
        return outcomeUnknown(template, before, fbResponse.getErrorMessage());
    }

    // ── Outcome handlers ──

    private TemplateResult handleSuccessBody(WhatsappTemplate template, TemplateAuditSnapshot before, JsonNode body) {
        if (body == null) {
            return outcomeUnknown(template, before, "Facebook returned 2xx with an empty body");
        }
        String metaResponse = body.toString();

        if (body.has(MetaGraph.Fields.ERROR)) {
            return rejected(template, before, metaResponse);
        }

        String metaTemplateId = textOrNull(body, MetaGraph.Fields.ID);
        String status = textOrNull(body, MetaGraph.Fields.STATUS);
        String category = textOrNull(body, MetaGraph.Fields.CATEGORY);

        if (metaTemplateId == null) {
            // 2xx without an id: we cannot tell whether it exists. Let the
            // reconciler look it up by name rather than guess.
            return outcomeUnknown(template, before, "Facebook returned 2xx without a template id: " + metaResponse);
        }

        try {
            commandService.markAsAcceptedByMeta(template, metaTemplateId, status, category, metaResponse);
        } catch (RuntimeException ex) {
            // Meta HAS the template; only our write failed. Do not surface a
            // 500 that would invite a retry (which Meta would reject as a
            // duplicate). The row stays SUBMITTED and the reconciler will
            // record this metaId.
            log.error("Meta accepted templateId={} (metaId={}) but saving the result failed; "
                    + "left SUBMITTED for reconciliation", template.getId(), metaTemplateId, ex);
            audit.templateSubmitted(before, template, SubmissionOutcome.PENDING_RECONCILIATION);
            TemplateResult result = templateMapper.mapToTemplateResponse(template);
            result.setMetaTemplateId(metaTemplateId);
            return result;
        }

        log.info("Template submitted to Meta: templateId={} metaId={} status={}",
                template.getId(), metaTemplateId, template.getStatus());
        audit.templateSubmitted(before, template, SubmissionOutcome.ACCEPTED);
        return templateMapper.mapToTemplateResponse(template);
    }

    /** Meta definitively refused the request. */
    private TemplateResult rejected(WhatsappTemplate template, TemplateAuditSnapshot before, String rawError) {
        JsonNode errorJson = parseJson(rawError);
        String userMessage = metaUserMessage(errorJson, rawError);

        log.warn("Meta rejected templateId={}: {}", template.getId(), userMessage);
        safeMarkFailed(template, userMessage, errorJson != null ? errorJson.toString() : null);
        audit.templateSubmitRejected(before, template, userMessage);

        TemplateResult result = templateMapper.mapToTemplateResponse(template);
        result.setErrorMessage(userMessage);
        result.setErrorPayload(errorJson);
        return result;
    }

    /**
     * We do not know whether Meta created the template. Leave it SUBMITTED
     * (it keeps holding the name, which is correct if Meta did create it) and
     * let the reconciler find out.
     */
    private TemplateResult outcomeUnknown(WhatsappTemplate template, TemplateAuditSnapshot before, String reason) {
        log.warn("Meta outcome unknown for templateId={} - left SUBMITTED for reconciliation. Reason: {}",
                template.getId(), reason);
        audit.templateSubmitted(before, template, SubmissionOutcome.PENDING_RECONCILIATION);
        return templateMapper.mapToTemplateResponse(template);
    }

    // ── Helpers ──

    /** Marking FAILED must never mask the original outcome with a second error. */
    private void safeMarkFailed(WhatsappTemplate template, String reason, String metaResponse) {
        try {
            commandService.markAsFailed(template, reason, metaResponse);
        } catch (RuntimeException ex) {
            log.error("Could not mark templateId={} FAILED (reason: {}); it stays SUBMITTED and "
                    + "will be reconciled", template.getId(), reason, ex);
        }
    }

    /**
     * Meta's error shape: {"error":{"message":"Invalid parameter",
     * "error_user_title":"...","error_user_msg":"Variables can't be at the
     * start or end of the template.", ...}}. The user message is the useful
     * one; fall back to title, then message, then the raw text.
     */
    private static String metaUserMessage(JsonNode errorJson, String raw) {
        if (errorJson != null) {
            JsonNode err = errorJson.path(MetaGraph.Fields.ERROR);
            for (String field : new String[] { MetaGraph.Fields.ERROR_USER_MSG, MetaGraph.Fields.ERROR_USER_TITLE, MetaGraph.Fields.MESSAGE }) {
                String value = textOrNull(err, field);
                if (value != null) {
                    return value;
                }
            }
        }
        return raw == null || raw.isBlank() ? TemplateConstants.RejectionReasons.META_REJECTED : raw;
    }

    /** Returns the parsed JSON, or null if {@code raw} is not JSON (meta_response is a JSON column). */
    private static JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(raw);
            return node != null && node.isContainerNode() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

}
