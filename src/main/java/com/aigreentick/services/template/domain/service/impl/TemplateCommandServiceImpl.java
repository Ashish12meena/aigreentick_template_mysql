package com.aigreentick.services.template.domain.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.common.error.ErrorCode;
import com.aigreentick.services.template.common.exception.DuplicateResourceException;
import com.aigreentick.services.template.common.exception.InvalidTemplateStateException;
import com.aigreentick.services.template.common.exception.ResourceNotFoundException;
import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.repository.WhatsappTemplateCommandRepository;
import com.aigreentick.services.template.domain.service.TemplateCommandService;
import com.aigreentick.services.template.domain.service.TemplateQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class TemplateCommandServiceImpl implements TemplateCommandService {

    private final WhatsappTemplateCommandRepository commandRepo;
    private final TemplateQueryService queryService;

    @Override
    public WhatsappTemplate save(WhatsappTemplate template) {
        return commandRepo.save(template);
    }

    @Override
    public List<WhatsappTemplate> saveAll(List<WhatsappTemplate> templates) {
        return commandRepo.saveAll(templates);
    }

    // ── Status transitions ──

    @Override
    public void markAsSubmitted(WhatsappTemplate template) {
        int changed = commandRepo.transitionStatus(
                template.getId(), TemplateStatus.DRAFT, TemplateStatus.SUBMITTED, Instant.now());
        if (changed == 0) {
            throw new InvalidTemplateStateException(String.format(
                    "Template id=%d is no longer a DRAFT (already submitted or deleted)", template.getId()));
        }
        template.setStatus(TemplateStatus.SUBMITTED);
    }

    @Override
    public boolean markAsAcceptedByMeta(WhatsappTemplate template, String metaTemplateId,
            String metaStatus, String metaCategory, String metaResponse) {

        Optional<WhatsappTemplate> row = loadIfSubmitted(template.getId(), "accepted-by-Meta");
        if (row.isEmpty()) {
            return false;
        }
        WhatsappTemplate managed = row.get();

        TemplateStatus status = TemplateStatus.parse(metaStatus).orElse(TemplateStatus.UNKNOWN);
        if (status == TemplateStatus.UNKNOWN || !status.isLive()) {
            // Meta accepted the request, so the template exists there: it must
            // stay live. A status we don't model (or a nonsensical DRAFT /
            // FAILED) is recorded as UNKNOWN; the raw value is kept for sync.
            log.warn("Unrecognised Meta status '{}' for templateId={} - stored as UNKNOWN",
                    metaStatus, template.getId());
            status = TemplateStatus.UNKNOWN;
        }

        managed.setMetaTemplateId(metaTemplateId);
        managed.setStatus(status);
        managed.setMetaStatusRaw(metaStatus);
        managed.setMetaResponse(metaResponse);
        managed.setRejectionReason(null);

        // Meta may re-categorise on create (e.g. UTILITY -> MARKETING).
        TemplateCategory category = parseCategory(metaCategory);
        if (category != null && category != managed.getCategory()) {
            log.info("Meta re-categorised templateId={} {} -> {}",
                    template.getId(), managed.getCategory(), category);
            managed.setPreviousCategory(managed.getCategory());
            managed.setCategory(category);
        }

        commandRepo.saveAndFlush(managed);
        copyOutcome(managed, template);
        return true;
    }

    @Override
    public boolean markAsFailed(WhatsappTemplate template, String errorMessage, String metaResponse) {
        Optional<WhatsappTemplate> row = loadIfSubmitted(template.getId(), "failed");
        if (row.isEmpty()) {
            return false;
        }
        WhatsappTemplate managed = row.get();
        managed.setStatus(TemplateStatus.FAILED);
        managed.setRejectionReason(errorMessage);
        managed.setMetaResponse(metaResponse);

        commandRepo.saveAndFlush(managed);
        copyOutcome(managed, template);
        return true;
    }

    /** Re-reads the row; empty (with a log line) if it is gone or no longer SUBMITTED. */
    private Optional<WhatsappTemplate> loadIfSubmitted(Long id, String outcome) {
        Optional<WhatsappTemplate> row = commandRepo.findById(id);
        if (row.isEmpty()) {
            log.warn("Cannot record {} outcome: templateId={} not found (deleted?)", outcome, id);
            return Optional.empty();
        }
        if (row.get().getStatus() != TemplateStatus.SUBMITTED) {
            log.info("Skipping {} outcome for templateId={}: status is already {}",
                    outcome, id, row.get().getStatus());
            return Optional.empty();
        }
        return row;
    }

    /** Keeps the caller's (detached) copy in step with what was committed. */
    private static void copyOutcome(WhatsappTemplate from, WhatsappTemplate to) {
        if (from == to) {
            return;
        }
        to.setStatus(from.getStatus());
        to.setMetaTemplateId(from.getMetaTemplateId());
        to.setMetaStatusRaw(from.getMetaStatusRaw());
        to.setMetaResponse(from.getMetaResponse());
        to.setRejectionReason(from.getRejectionReason());
        to.setCategory(from.getCategory());
        to.setPreviousCategory(from.getPreviousCategory());
        to.setUpdatedAt(from.getUpdatedAt());
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

    @Override
    public void flush() {
        commandRepo.flush();
    }

    // ── Deletes ──

    @Override
    public int softDeleteById(Long id, Long projectId) {
        int deleted = commandRepo.softDeleteById(id, projectId, Instant.now());
        if (deleted == 0) {
            throw new ResourceNotFoundException(ErrorCode.TEMPLATE_NOT_FOUND, "Template", "id", id);
        }
        log.info("Soft-deleted template id={} projectId={}", id, projectId);
        return deleted;
    }

    @Override
    public int softDeleteAllByProject(Long projectId) {
        int deleted = commandRepo.softDeleteAllByProject(projectId, Instant.now());
        log.info("Bulk soft-deleted {} templates for projectId={}", deleted, projectId);
        return deleted;
    }

    @Override
    public int softDeleteStaleByMetaIds(Set<String> metaIds, Long projectId) {
        if (metaIds == null || metaIds.isEmpty())
            return 0;
        return commandRepo.softDeleteStaleByMetaIds(metaIds, projectId, Instant.now());
    }

    // ── Validation ──

    @Override
    public void ensureNoDuplicate(String wabaId, String name, String language, Long excludeTemplateId) {
        // Only LIVE templates count: a DRAFT or FAILED row with the same name
        // does not exist on Meta and must not block (re)creating it.
        if (queryService.existsLive(wabaId, name, language, excludeTemplateId)) {
            throw new DuplicateResourceException(ErrorCode.TEMPLATE_ALREADY_EXISTS, String.format(
                    "Template '%s' (%s) already exists on WABA %s", name, language, wabaId));
        }
    }
}