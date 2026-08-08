package com.aigreentick.services.template.domain.service.impl;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.common.exception.InvalidTemplateStateException;
import com.aigreentick.services.template.common.exception.ResourceNotFoundException;
import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.repository.WhatsappTemplateQueryRepository;
import com.aigreentick.services.template.domain.service.TemplateQueryService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TemplateQueryServiceImpl implements TemplateQueryService {

    private final WhatsappTemplateQueryRepository queryRepo;

    @Override
    public WhatsappTemplate getByIdAndProject(Long id, Long projectId) {
        return queryRepo.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template", "id", id));
    }

    @Override
    public WhatsappTemplate getByNameLanguageAndWaba(
            Long projectId, String name, String language, String wabaId) {
        return queryRepo.findByWabaIdAndNameAndLanguageAndProjectId(
                wabaId, name, language, projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Template not found: name='%s' language='%s'", name, language)));
    }

    @Override
    public WhatsappTemplate getDraftByIdAndProject(Long id, Long projectId) {
        WhatsappTemplate template = getByIdAndProject(id, projectId);
        if (template.getStatus() != TemplateStatus.DRAFT) {
            throw new InvalidTemplateStateException(
                    String.format("Template id=%d is not in DRAFT status. Current status: %s",
                            id, template.getStatus()));
        }
        return template;
    }

    @Override
    public Page<WhatsappTemplate> listByProject(
            Long projectId, TemplateStatus status, TemplateCategory category,
            String search, int page, int size, String sortBy, String sortDir) {

        Sort sort = Sort.by(
                sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,
                sortBy);

        return queryRepo.findAllByFilters(
                projectId, status, category, search, PageRequest.of(page, size, sort));
    }

    @Override
    public boolean existsNonDraft(String wabaId, String name, String language, Long excludeTemplateId) {
        return queryRepo.existsDuplicate(
                wabaId, name, language, TemplateStatus.DRAFT, excludeTemplateId);
    }

    @Override
    public Set<String> findSyncedMetaIds(Long projectId, String wabaId) {
        return queryRepo.findMetaIdsByProjectAndWabaExcludingDrafts(projectId, wabaId);
    }

    @Override
    public List<WhatsappTemplate> findAllByMetaIds(Set<String> metaIds, Long projectId) {
        if (metaIds == null || metaIds.isEmpty())
            return List.of();
        return queryRepo.findAllByMetaTemplateIdInAndProjectId(metaIds, projectId);
    }

    @Override
    public long countActiveByProject(Long projectId) {
        return queryRepo.countByProjectIdAndDeletedAtIsNull(projectId);
    }
}