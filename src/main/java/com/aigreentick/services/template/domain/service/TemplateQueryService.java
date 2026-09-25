package com.aigreentick.services.template.domain.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    /** True if another LIVE template (not DRAFT / FAILED / deleted) uses this name on the WABA. */
    boolean existsLive(String wabaId, String name, String language, Long excludeTemplateId);

    /** SUBMITTED templates whose Meta outcome was not recorded before {@code cutoff}. */
    List<WhatsappTemplate> findStuckSubmitted(Instant cutoff, int limit);

    /** A live row for this name with no Meta id yet (an unresolved SUBMITTED row). */
    Optional<WhatsappTemplate> findLiveWithoutMetaId(String wabaId, String name, String language);

    Set<String> findSyncedMetaIds(Long projectId, String wabaAccountId);

    List<WhatsappTemplate> findAllByMetaIds(Set<String> metaIds, Long projectId);

    long countActiveByProject(Long projectId);

    WhatsappTemplate getDetailByIdAndProject(Long id, Long projectId);
}