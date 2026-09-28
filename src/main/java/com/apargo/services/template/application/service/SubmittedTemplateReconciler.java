package com.apargo.services.template.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.apargo.services.template.application.audit.TemplateAuditEvents;
import com.apargo.services.template.application.audit.TemplateAuditSnapshot;
import com.apargo.services.template.application.dto.TenantScope;
import com.apargo.services.template.application.dto.client.AccessTokenIdentifier;
import com.apargo.services.template.application.dto.client.FacebookApiResponse;
import com.apargo.services.template.application.dto.command.ReconcileSubmittedCommand;
import com.apargo.services.template.application.dto.result.ReconcileSubmittedResult;
import com.apargo.services.template.application.port.out.FacebookTemplateSyncPort;
import com.apargo.services.template.application.port.out.WabaCredentialPort;
import com.apargo.services.template.common.constant.MetaGraph;
import com.apargo.services.template.common.constant.TemplateConstants;
import com.apargo.services.template.domain.model.WhatsappTemplate;
import com.apargo.services.template.domain.service.TemplateCommandService;
import com.apargo.services.template.domain.service.TemplateQueryService;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Settles templates stuck in SUBMITTED. Pure application logic: it has no
 * schedule and reads no configuration. What triggers it, and with which
 * settings, is the caller's concern - today {@code TemplateReconcileScheduler}
 * in infrastructure, the same way a controller triggers a use case.
 *
 * <h2>Why rows get stuck</h2>
 * {@link MetaTemplateSubmissionService} commits SUBMITTED before calling Meta
 * and leaves it there whenever it cannot know what Meta did: a timeout, a Meta
 * 5xx, or a crash / DB error after Meta accepted. That is deliberate - a
 * SUBMITTED row keeps holding the name, which is right if Meta created it.
 *
 * <h2>What a run does</h2>
 * For each row SUBMITTED longer than {@code minAge}: look the template up on
 * Meta by (name, language).
 * <ul>
 *   <li><b>Found</b> - record Meta's id and status (PENDING / APPROVED / ...).</li>
 *   <li><b>Not found, older than {@code giveUpAfter}</b> - the submission never
 *       reached Meta: mark FAILED, which frees the name.</li>
 *   <li><b>Not found yet, or Meta / waba-service unreachable</b> - leave it;
 *       the next run tries again.</li>
 * </ul>
 *
 * <h2>Safe to run on several instances</h2>
 * Every write goes through TemplateCommandService, which only changes a row
 * that is still SUBMITTED. If two instances (or a sync) settle the same row,
 * the second write is a logged no-op.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SubmittedTemplateReconciler {

    private final TemplateQueryService queryService;
    private final TemplateCommandService commandService;
    private final WabaCredentialPort wabaCredentialPort;
    private final FacebookTemplateSyncPort facebookSyncPort;
    private final TemplateAuditEvents audit;

    public ReconcileSubmittedResult reconcile(ReconcileSubmittedCommand command) {
        Instant now = Instant.now();
        List<WhatsappTemplate> stuck = queryService.findStuckSubmitted(
                now.minus(command.minAge()), command.batchSize());
        if (stuck.isEmpty()) {
            return ReconcileSubmittedResult.empty();
        }

        log.info("[RECONCILE] {} template(s) stuck in SUBMITTED - checking Meta", stuck.size());
        int accepted = 0;
        int failed = 0;
        int unresolved = 0;

        for (WhatsappTemplate template : stuck) {
            try {
                switch (reconcileOne(template, now, command)) {
                    case ACCEPTED -> accepted++;
                    case FAILED -> failed++;
                    case UNRESOLVED -> unresolved++;
                }
            } catch (RuntimeException ex) {
                // One bad row must not stop the batch.
                unresolved++;
                log.warn("[RECONCILE] templateId={} could not be checked, will retry: {}",
                        template.getId(), ex.getMessage());
            }
        }

        ReconcileSubmittedResult result =
                new ReconcileSubmittedResult(stuck.size(), accepted, failed, unresolved);
        log.info("[RECONCILE] done - {}", result);
        return result;
    }

    private enum Outcome { ACCEPTED, FAILED, UNRESOLVED }

    private Outcome reconcileOne(WhatsappTemplate template, Instant now, ReconcileSubmittedCommand command) {
        AccessTokenIdentifier credentials = wabaCredentialPort.getWhatsappAccountWabaAccessToken(
                template.getWabaId(), TenantScope.of(template));

        // Page size is left to the adapter (facebook-service.template-page-size).
        FacebookApiResponse<JsonNode> response = facebookSyncPort.getAllTemplates(
                template.getWabaId(),
                credentials.getAccessToken(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(template.getName()),
                Optional.empty(),
                Optional.empty());

        if (response == null || !response.isSuccess() || response.getData() == null) {
            log.warn("[RECONCILE] Meta lookup failed for templateId={} - will retry. {}",
                    template.getId(), response != null ? response.getErrorMessage() : "null response");
            return Outcome.UNRESOLVED;
        }

        Optional<JsonNode> onMeta = findExact(response.getData(), template.getName(), template.getLanguage());

        if (onMeta.isPresent()) {
            JsonNode node = onMeta.get();
            String metaId = node.path(MetaGraph.Fields.ID).asText(null);
            if (metaId == null || metaId.isBlank()) {
                return Outcome.UNRESOLVED;
            }
            TemplateAuditSnapshot before = TemplateAuditSnapshot.of(template);
            boolean changed = commandService.markAsAcceptedByMeta(template, metaId,
                    node.path(MetaGraph.Fields.STATUS).asText(null),
                    node.path(MetaGraph.Fields.CATEGORY).asText(null),
                    node.toString());
            if (changed) {
                log.info("[RECONCILE] templateId={} found on Meta metaId={} status={}",
                        template.getId(), metaId, template.getStatus());
                audit.templateChangedOnMeta(before, template);
            }
            return changed ? Outcome.ACCEPTED : Outcome.UNRESOLVED;
        }

        Instant submittedAt = template.getUpdatedAt() != null ? template.getUpdatedAt() : template.getCreatedAt();
        if (submittedAt != null && submittedAt.isBefore(now.minus(command.giveUpAfter()))) {
            TemplateAuditSnapshot before = TemplateAuditSnapshot.of(template);
            boolean changed = commandService.markAsFailed(template,
                    String.format(TemplateConstants.RejectionReasons.NOT_RECEIVED_BY_META,
                            command.giveUpAfter().toMinutes()),
                    null);
            if (changed) {
                log.warn("[RECONCILE] templateId={} never reached Meta - marked FAILED", template.getId());
                audit.templateSubmissionNotReceived(before, template);
            }
            return changed ? Outcome.FAILED : Outcome.UNRESOLVED;
        }

        return Outcome.UNRESOLVED;
    }

    /** Meta's {@code name} filter is not exact, so match name and language here. */
    private static Optional<JsonNode> findExact(JsonNode body, String name, String language) {
        JsonNode data = body.path(MetaGraph.Fields.DATA);
        if (!data.isArray()) {
            return Optional.empty();
        }
        for (JsonNode node : data) {
            if (name.equals(node.path(MetaGraph.Fields.NAME).asText(null))
                    && language.equalsIgnoreCase(node.path(MetaGraph.Fields.LANGUAGE).asText(""))) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }
}
