package com.apargo.services.template.application.usecase;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.apargo.services.template.api.request.BaseTemplateRequestDto;
import com.apargo.services.template.application.audit.TemplateAuditEvents;
import com.apargo.services.template.application.audit.TemplateAuditSnapshot;
import com.apargo.services.template.application.dto.TenantScope;
import com.apargo.services.template.application.dto.client.AccessTokenIdentifier;
import com.apargo.services.template.application.dto.client.FacebookApiResponse;
import com.apargo.services.template.application.dto.command.EditTemplateCommand;
import com.apargo.services.template.application.dto.result.TemplateResult;
import com.apargo.services.template.application.mapper.WhatsappTemplateMapper;
import com.apargo.services.template.application.port.in.EditTemplateUseCase;
import com.apargo.services.template.application.port.out.FacebookTemplatePort;
import com.apargo.services.template.application.port.out.WabaCredentialPort;
import com.apargo.services.template.application.service.MetaTemplateSubmissionService;
import com.apargo.services.template.application.validation.TemplateValidationService;
import com.apargo.services.template.application.validation.Violation;
import com.apargo.services.template.common.exception.ExternalServiceException;
import com.apargo.services.template.common.exception.InvalidTemplateStateException;
import com.apargo.services.template.common.exception.TemplateRuleViolationException;
import com.apargo.services.template.common.exception.WhatsappCredentialsNotFoundException;
import com.apargo.services.template.common.util.helper.MetaTemplateJson;
import com.apargo.services.template.domain.enums.TemplateStatus;
import com.apargo.services.template.domain.model.WhatsappTemplate;
import com.apargo.services.template.domain.model.WhatsappTemplateComponent;
import com.apargo.services.template.domain.model.WhatsappTemplateVariable;
import com.apargo.services.template.domain.service.TemplateCommandService;
import com.apargo.services.template.domain.service.TemplateQueryService;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Edits a template that already exists on Meta ({@code POST /{meta-template-id}})
 * and records the new contents locally with status PENDING. Meta re-reviews
 * it; the status webhook then moves it on.
 *
 * <h2>What Meta allows</h2>
 * <ul>
 *   <li>APPROVED, REJECTED or PAUSED templates only.</li>
 *   <li>Name and language never change.</li>
 *   <li>Category only while the template is not APPROVED.</li>
 *   <li>An APPROVED template: about one edit a day and 10 per 30 days. Meta
 *       enforces this and its refusal is returned as a 422.</li>
 * </ul>
 *
 * <h2>Why this method is not @Transactional</h2>
 * Same reason as create: no DB connection is held while waiting on Meta. The
 * local write happens afterwards, in its own short transaction, and only if
 * Meta accepted the edit. If Meta does not answer, nothing local changes and
 * the caller is told to sync.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EditTemplateUseCaseImpl implements EditTemplateUseCase {

    private static final Set<TemplateStatus> EDITABLE =
            EnumSet.of(TemplateStatus.APPROVED, TemplateStatus.REJECTED, TemplateStatus.PAUSED);

    private final TemplateQueryService queryService;
    private final TemplateCommandService commandService;
    private final TemplateValidationService templateValidationService;
    private final WhatsappTemplateMapper templateMapper;
    private final WabaCredentialPort wabaCredentialPort;
    private final FacebookTemplatePort ftp;
    private final TemplateAuditEvents audit;
    private final TransactionTemplate transactionTemplate;

    @Override
    public TemplateResult execute(EditTemplateCommand command) {
        WhatsappTemplate existing = queryService.getByIdAndProject(command.getTemplateId(), command.getProjectId());
        BaseTemplateRequestDto templateReq = command.getTemplateData();

        // Step 1: Is this an edit Meta will take?
        if (!EDITABLE.contains(existing.getStatus())
                || existing.getMetaTemplateId() == null || existing.getMetaTemplateId().isBlank()) {
            throw new InvalidTemplateStateException(String.format(
                    "Template id=%d is %s; only APPROVED, REJECTED or PAUSED templates can be edited "
                            + "(use the draft endpoint for drafts)", existing.getId(), existing.getStatus()));
        }
        checkFixedFields(existing, templateReq);
        templateValidationService.validate(templateReq);

        // Step 2: Call Meta. No transaction held.
        boolean includeCategory = existing.getStatus() != TemplateStatus.APPROVED;
        String body = MetaTemplateJson.writeEdit(templateReq, includeCategory);
        AccessTokenIdentifier credentials = wabaCredentialPort.getWhatsappAccountWabaAccessToken(
                existing.getWabaId(), TenantScope.of(existing));
        if (credentials == null || credentials.getAccessToken() == null || credentials.getAccessToken().isBlank()) {
            throw new WhatsappCredentialsNotFoundException("Access token not found for wabaId: " + existing.getWabaId());
        }

        FacebookApiResponse<JsonNode> response = ftp.editTemplate(
                existing.getMetaTemplateId(), body, credentials.getAccessToken());

        if (response == null || !response.isSuccess()) {
            if (response != null && response.isClientError()) {
                String message = MetaTemplateSubmissionService.metaUserMessage(response.getErrorMessage());
                log.warn("Meta refused edit of templateId={}: {}", existing.getId(), message);
                throw new TemplateRuleViolationException(List.of(
                        Violation.of("template", "META_EDIT_REJECTED", message)));
            }
            // Meta may or may not have applied it; a sync settles the local copy.
            log.error("Meta edit outcome unknown for templateId={}: {}", existing.getId(),
                    response != null ? response.getErrorMessage() : "null response");
            throw new ExternalServiceException(
                    "Meta did not confirm the edit; run a template sync to see its current state");
        }

        // Step 3: Meta accepted - record the new contents, back under review.
        String submissionPayload = MetaTemplateJson.write(templateReq);
        return transactionTemplate.execute(status -> applyLocally(command, submissionPayload));
    }

    private TemplateResult applyLocally(EditTemplateCommand command, String submissionPayload) {
        BaseTemplateRequestDto templateReq = command.getTemplateData();
        WhatsappTemplate row = queryService.getByIdAndProject(command.getTemplateId(), command.getProjectId());
        TemplateAuditSnapshot before = TemplateAuditSnapshot.of(row);

        row.setCategory(templateReq.getCategory());
        row.setSubmissionPayload(submissionPayload);
        row.setStatus(TemplateStatus.PENDING);
        row.setMetaStatusRaw(TemplateStatus.PENDING.name());
        row.setRejectionReason(null);

        row.getComponents().clear();
        if (templateReq.getComponents() != null && !templateReq.getComponents().isEmpty()) {
            List<WhatsappTemplateComponent> components = templateMapper.mapComponents(templateReq.getComponents(), row);
            components.forEach(row::addComponent);
        }
        row.getVariables().clear();
        if (command.getVariables() != null && !command.getVariables().isEmpty()) {
            List<WhatsappTemplateVariable> variables = templateMapper.mapVariables(command.getVariables(), row);
            variables.forEach(row::addVariable);
        }

        WhatsappTemplate saved = commandService.save(row);
        commandService.flush();
        log.info("Template edited on Meta id={} metaId={} - now PENDING", saved.getId(), saved.getMetaTemplateId());

        // Published only if this transaction commits.
        audit.templateUpdated(before, saved);
        return templateMapper.mapToTemplateResponse(saved);
    }

    /** Meta keys a template on name + language and freezes the category once approved. */
    private static void checkFixedFields(WhatsappTemplate existing, BaseTemplateRequestDto req) {
        List<Violation> violations = new ArrayList<>();
        if (!existing.getName().equals(req.getName())) {
            violations.add(Violation.of("template.name", "META_EDIT_NAME_FIXED",
                    "A template's name cannot be changed; it is '" + existing.getName() + "'"));
        }
        if (!existing.getLanguage().equalsIgnoreCase(req.getLanguage())) {
            violations.add(Violation.of("template.language", "META_EDIT_LANGUAGE_FIXED",
                    "A template's language cannot be changed; it is '" + existing.getLanguage() + "'"));
        }
        if (existing.getStatus() == TemplateStatus.APPROVED && existing.getCategory() != req.getCategory()) {
            violations.add(Violation.of("template.category", "META_EDIT_APPROVED_CATEGORY_FIXED",
                    "The category of an APPROVED template cannot be changed; it is " + existing.getCategory()));
        }
        if (!violations.isEmpty()) {
            throw new TemplateRuleViolationException(violations);
        }
    }
}
