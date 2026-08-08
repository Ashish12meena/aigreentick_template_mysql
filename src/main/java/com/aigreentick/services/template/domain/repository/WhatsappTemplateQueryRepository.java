package com.aigreentick.services.template.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;

/**
 * Read-only queries for WhatsApp templates.
 * All finder/search operations live here — no mutations.
 */
@Repository
public interface WhatsappTemplateQueryRepository extends JpaRepository<WhatsappTemplate, Long> {

    // ── Single-record lookups ──

    Optional<WhatsappTemplate> findByIdAndProjectId(Long id, Long projectId);

    Optional<WhatsappTemplate> findByProjectIdAndNameAndLanguageAndWabaId(
            Long projectId, String name, String language, String wabaId);

    // ── Existence checks ──

    boolean existsByProjectIdAndNameAndLanguageAndWabaIdAndStatusNot(
            Long projectId, String name, String language, String wabaId, TemplateStatus excludedStatus);

    // ── Paginated listing with filters ──

    @Query("""
            SELECT t FROM WhatsappTemplate t
            WHERE t.projectId = :projectId      
              AND (:status IS NULL OR t.status = :status)
              AND (:category IS NULL OR t.category = :category)
              AND (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<WhatsappTemplate> findAllByFilters(
            @Param("projectId") Long projectId,
            @Param("status") TemplateStatus status,
            @Param("category") TemplateCategory category,
            @Param("search") String search,
            Pageable pageable);

    // ── Sync-related lookups ──

    @Query("""
            SELECT t.metaTemplateId FROM WhatsappTemplate t
            WHERE t.projectId = :projectId
              AND t.wabaId = :wabaId
              AND t.status <> 'DRAFT'
              AND t.metaTemplateId IS NOT NULL
            """)
    Set<String> findMetaIdsByProjectAndWabaExcludingDrafts(
            @Param("projectId") Long projectId,
            @Param("wabaId") String wabaId);

    List<WhatsappTemplate> findAllByMetaTemplateIdInAndProjectId(
            Set<String> metaTemplateIds, Long projectId);

    // ── Counts ──

    long countByProjectIdAndDeletedAtIsNull(Long projectId);


    /**
     * Identity is (wabaId, name, language); projectId is applied as a tenancy
     * filter so one project cannot read another's template even on the same WABA.
     */
    Optional<WhatsappTemplate> findByWabaIdAndNameAndLanguageAndProjectId(
            String wabaId, String name, String language, Long projectId);

    // ── Existence checks ──

    /**
     * Duplicate check at WABA scope — deliberately NOT filtered by project,
     * because Meta rejects a duplicate (waba, name, language) regardless of
     * which project of ours created it.
     *
     * {@code excludeTemplateId} lets the draft-update path ignore the row it is
     * itself editing; pass null on create.
     *
     * Soft-deleted rows are excluded by @SQLRestriction on the entity.
     */
    @Query("""
            SELECT COUNT(t) > 0 FROM WhatsappTemplate t
            WHERE t.wabaId = :wabaId
              AND t.name = :name
              AND t.language = :language
              AND t.status <> :excludedStatus
              AND (:excludeTemplateId IS NULL OR t.id <> :excludeTemplateId)
            """)
    boolean existsDuplicate(
            @Param("wabaId") String wabaId,
            @Param("name") String name,
            @Param("language") String language,
            @Param("excludedStatus") TemplateStatus excludedStatus,
            @Param("excludeTemplateId") Long excludeTemplateId);
}