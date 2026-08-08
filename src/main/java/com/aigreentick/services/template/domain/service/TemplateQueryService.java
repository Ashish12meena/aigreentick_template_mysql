package com.aigreentick.services.template.domain.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;

import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;

/**
 * Read-only operations on WhatsApp templates.
 */
public interface TemplateQueryService {

    WhatsappTemplate getByIdAndProject(Long id, Long projectId);

    WhatsappTemplate getByNameLanguageAndWaba(Long projectId, String name, String language, String wabaAccountId);

    WhatsappTemplate getDraftByIdAndProject(Long id, Long projectId);

    Page<WhatsappTemplate> listByProject(
            Long projectId, TemplateStatus status, TemplateCategory category,
            String search, int page, int size, String sortBy, String sortDir);

    boolean existsNonDraft(String wabaId, String name, String language, Long excludeTemplateId);

    Set<String> findSyncedMetaIds(Long projectId, String wabaAccountId);

    List<WhatsappTemplate> findAllByMetaIds(Set<String> metaIds, Long projectId);

    long countActiveByProject(Long projectId);
}