package com.apargo.services.template.application.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.apargo.services.template.application.audit.TemplateAuditEvents;
import com.apargo.services.template.application.audit.TemplateAuditSnapshot;
import com.apargo.services.template.common.constant.MetaGraph;
import com.apargo.services.template.domain.enums.TemplateCategory;
import com.apargo.services.template.domain.enums.TemplateQualityRating;
import com.apargo.services.template.domain.enums.TemplateStatus;
import com.apargo.services.template.domain.model.WhatsappTemplate;
import com.apargo.services.template.domain.service.TemplateCommandService;
import com.apargo.services.template.domain.service.TemplateQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies Meta's template webhooks (status, quality, category) to the local
 * rows. Transport-free: {@code TemplateWebhookListener} parses the Kafka
 * record and calls in here.
 *
 * <h2>Matching</h2>
 * By {@code meta_template_id}. A template we do not have (created outside the
 * platform, or deleted here) is ignored; a manual sync picks it up.
 *
 * <h2>The one race</h2>
 * Meta can approve a template, and send the webhook, before
 * {@link MetaTemplateSubmissionService} has stored Meta's id. That row is
 * still SUBMITTED with no Meta id, so the lookup misses; the status call
 * reports {@link Outcome#AWAITING_SUBMISSION} and the listener retries.
 *
 * <h2>Repeats</h2>
 * Every write sets an absolute value, so a redelivered event is harmless.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateMetaEventService {

    private static final int META_STATUS_RAW_MAX = 64;

    private final TemplateQueryService queryService;
    private final TemplateCommandService commandService;
    private final TemplateAuditEvents audit;

    public enum Outcome {
        APPLIED,
        /** No local row for this Meta id. */
        UNKNOWN_TEMPLATE,
        /** A matching SUBMITTED row has no Meta id yet: retry shortly. */
        AWAITING_SUBMISSION
    }

    /**
     * {@code message_template_status_update}.
     *
     * @param event  Meta's new status (APPROVED, REJECTED, PAUSED, ...)
     * @param reason Meta's reason code; {@code NONE} when there is none
     * @param detail {@code other_info.description}, if any
     */
    @Transactional
    public Outcome applyStatus(String wabaId, String metaTemplateId, String name, String language,
            String event, String reason, String detail) {

        List<WhatsappTemplate> rows = queryService.findAllByMetaId(metaTemplateId);
        if (rows.isEmpty()) {
            if (wabaId != null && name != null && language != null
                    && queryService.findLiveWithoutMetaId(wabaId, name, language).isPresent()) {
                return Outcome.AWAITING_SUBMISSION;
            }
            log.info("[META-EVENT] status {} for unknown metaId={} ({} / {}) - ignored",
                    event, metaTemplateId, name, language);
            return Outcome.UNKNOWN_TEMPLATE;
        }

        if (MetaGraph.Webhook.EVENT_DELETED.equalsIgnoreCase(event)) {
            for (WhatsappTemplate row : rows) {
                commandService.softDeleteStaleByMetaIds(Set.of(metaTemplateId), row.getProjectId());
                log.info("[META-EVENT] templateId={} deleted on Meta - soft-deleted", row.getId());
            }
            return Outcome.APPLIED;
        }

        Optional<TemplateStatus> status = toStatus(event);
        for (WhatsappTemplate row : rows) {
            TemplateAuditSnapshot before = TemplateAuditSnapshot.of(row);
            row.setMetaStatusRaw(truncate(event, META_STATUS_RAW_MAX));

            if (status.isPresent()) {
                row.setStatus(status.get());
                if (status.get() == TemplateStatus.REJECTED) {
                    row.setRejectionReason(rejectionReason(reason, detail));
                } else if (status.get() == TemplateStatus.APPROVED) {
                    row.setRejectionReason(null);
                }
            } else {
                // FLAGGED, IN_APPEAL, LIMIT_EXCEEDED, ...: not modelled. Keep the
                // current status; the raw value is recorded for the next sync.
                log.warn("[META-EVENT] templateId={} unmodelled Meta status '{}' - status kept as {}",
                        row.getId(), event, row.getStatus());
            }

            commandService.save(row);
            log.info("[META-EVENT] templateId={} metaId={} status {} -> {}",
                    row.getId(), metaTemplateId, before.status(), row.getStatus());
            audit.templateChangedOnMeta(before, row);
        }
        return Outcome.APPLIED;
    }

    /** {@code message_template_quality_update}. */
    @Transactional
    public Outcome applyQuality(String metaTemplateId, String newQualityScore) {
        List<WhatsappTemplate> rows = queryService.findAllByMetaId(metaTemplateId);
        if (rows.isEmpty()) {
            return Outcome.UNKNOWN_TEMPLATE;
        }
        TemplateQualityRating rating = TemplateQualityRating.fromApi(newQualityScore);
        for (WhatsappTemplate row : rows) {
            log.info("[META-EVENT] templateId={} quality {} -> {}", row.getId(), row.getQualityRating(), rating);
            row.setQualityRating(rating);
            commandService.save(row);
        }
        return Outcome.APPLIED;
    }

    /**
     * {@code template_category_update} once the change has happened. The
     * advance notice (no {@code new_category}) is ignored by the caller.
     */
    @Transactional
    public Outcome applyCategory(String metaTemplateId, String newCategory) {
        List<WhatsappTemplate> rows = queryService.findAllByMetaId(metaTemplateId);
        if (rows.isEmpty()) {
            return Outcome.UNKNOWN_TEMPLATE;
        }
        TemplateCategory category = parseCategory(newCategory);
        if (category == null) {
            log.warn("[META-EVENT] metaId={} unknown category '{}' - ignored", metaTemplateId, newCategory);
            return Outcome.APPLIED;
        }
        for (WhatsappTemplate row : rows) {
            if (row.getCategory() == category) {
                continue;
            }
            TemplateAuditSnapshot before = TemplateAuditSnapshot.of(row);
            row.setPreviousCategory(row.getCategory());
            row.setCategory(category);
            commandService.save(row);
            log.info("[META-EVENT] templateId={} category {} -> {}", row.getId(), before.category(), category);
            audit.templateChangedOnMeta(before, row);
        }
        return Outcome.APPLIED;
    }

    /** Only statuses Meta can put a live template in; anything else is empty. */
    private static Optional<TemplateStatus> toStatus(String event) {
        if (MetaGraph.Webhook.EVENT_REINSTATED.equalsIgnoreCase(event)) {
            return Optional.of(TemplateStatus.APPROVED);
        }
        return TemplateStatus.parse(event).filter(s -> switch (s) {
            case PENDING, APPROVED, REJECTED, PAUSED, DISABLED -> true;
            default -> false;
        });
    }

    private static String rejectionReason(String reason, String detail) {
        String code = reason == null || reason.isBlank()
                || MetaGraph.Webhook.REASON_NONE.equalsIgnoreCase(reason) ? null : reason;
        if (detail == null || detail.isBlank()) {
            return code;
        }
        return code == null ? detail : code + ": " + detail;
    }

    private static TemplateCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TemplateCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
