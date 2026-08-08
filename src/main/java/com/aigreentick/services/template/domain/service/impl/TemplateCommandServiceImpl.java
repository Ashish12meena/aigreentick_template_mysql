package com.aigreentick.services.template.domain.service.impl;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.common.exception.DuplicateResourceException;
import com.aigreentick.services.template.common.exception.ResourceNotFoundException;
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
        template.setStatus(TemplateStatus.SUBMITTED);
        commandRepo.save(template);
    }

    @Override
    public void markAsSucceeded(WhatsappTemplate template,
            String metaTemplateId, String status, String metaResponse) {
        template.setMetaTemplateId(metaTemplateId);
        template.setStatus(TemplateStatus.valueOf(status.toUpperCase()));
        template.setMetaResponse(metaResponse);
        commandRepo.save(template);
    }

    @Override
    public void markAsFailed(WhatsappTemplate template, String errorMessage, String metaResponse) {
        template.setStatus(TemplateStatus.FAILED);
        template.setRejectionReason(errorMessage);
        template.setMetaResponse(metaResponse);
        commandRepo.save(template);
    }

    @Override
    public void markAsNewCreated(WhatsappTemplate template, String metaTemplateId, String status,
            String metaResponse) {
        template.setMetaTemplateId(metaTemplateId);
        template.setStatus(TemplateStatus.valueOf(status.toUpperCase()));
        template.setMetaResponse(metaResponse);
        commandRepo.save(template);
    }

    // ── Deletes ──

    @Override
    public int softDeleteById(Long id, Long projectId) {
        int deleted = commandRepo.softDeleteById(id, projectId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Template", "id", id);
        }
        log.info("Soft-deleted template id={} projectId={}", id, projectId);
        return deleted;
    }

    @Override
    public int softDeleteAllByProject(Long projectId) {
        int deleted = commandRepo.softDeleteAllByProject(projectId);
        log.info("Bulk soft-deleted {} templates for projectId={}", deleted, projectId);
        return deleted;
    }

    @Override
    public int softDeleteStaleByMetaIds(Set<String> metaIds, Long projectId) {
        if (metaIds == null || metaIds.isEmpty())
            return 0;
        return commandRepo.softDeleteStaleByMetaIds(metaIds, projectId);
    }

    // ── Validation ──

    @Override
    public void ensureNoDuplicate(String wabaId, String name, String language, Long excludeTemplateId) {
        if (queryService.existsNonDraft(wabaId, name, language, excludeTemplateId)) {
            throw new DuplicateResourceException(String.format(
                    "Template '%s' (%s) already exists on WABA %s", name, language, wabaId));
        }
    }
}