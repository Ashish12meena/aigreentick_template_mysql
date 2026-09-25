package com.aigreentick.services.template.domain.repository;

import java.time.Instant;
import java.util.Collection;
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

                        
        @Query("select t from WhatsappTemplate t left join fetch t.components where t.id = :id and t.projectId = :projectId")
        Optional<WhatsappTemplate> findDetailByIdAndProjectId(@Param("id") Long id, @Param("projectId") Long projectId);

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
         * Only LIVE rows count: pass the non-live statuses (DRAFT, FAILED) in
         * {@code notLiveStatuses}. This mirrors uk_waba_template_live, which is
         * the real guarantee; this query exists to give a clean 409 message
         * before the insert instead of a constraint violation.
         *
         * {@code excludeTemplateId} lets the draft-update / draft-submit paths
         * ignore the row they are acting on; pass null on create.
         *
         * Soft-deleted rows are excluded by @SQLRestriction on the entity.
         */
        @Query("""
                        SELECT COUNT(t) > 0 FROM WhatsappTemplate t
                        WHERE t.wabaId = :wabaId
                          AND t.name = :name
                          AND t.language = :language
                          AND t.status NOT IN :notLiveStatuses
                          AND (:excludeTemplateId IS NULL OR t.id <> :excludeTemplateId)
                        """)
        boolean existsLiveDuplicate(
                        @Param("wabaId") String wabaId,
                        @Param("name") String name,
                        @Param("language") String language,
                        @Param("notLiveStatuses") Collection<TemplateStatus> notLiveStatuses,
                        @Param("excludeTemplateId") Long excludeTemplateId);

        /**
         * Templates stuck in SUBMITTED: the Meta call was made but its outcome
         * was never recorded (timeout, crash, DB error after Meta accepted).
         * Oldest first so a backlog drains in order.
         */
        @Query("""
                        SELECT t FROM WhatsappTemplate t
                        WHERE t.status = com.aigreentick.services.template.domain.enums.TemplateStatus.SUBMITTED
                          AND t.updatedAt < :cutoff
                        ORDER BY t.updatedAt ASC
                        """)
        List<WhatsappTemplate> findSubmittedUpdatedBefore(
                        @Param("cutoff") Instant cutoff,
                        Pageable pageable);

        /**
         * A live local row for this name that has no Meta id yet — i.e. a
         * SUBMITTED row whose Meta outcome was never recorded. Used by sync to
         * adopt that row instead of inserting a second live row for the same
         * Meta template.
         */
        @Query("""
                        SELECT t FROM WhatsappTemplate t
                        WHERE t.wabaId = :wabaId
                          AND t.name = :name
                          AND t.language = :language
                          AND t.metaTemplateId IS NULL
                          AND t.status NOT IN :notLiveStatuses
                        """)
        List<WhatsappTemplate> findLiveWithoutMetaId(
                        @Param("wabaId") String wabaId,
                        @Param("name") String name,
                        @Param("language") String language,
                        @Param("notLiveStatuses") Collection<TemplateStatus> notLiveStatuses);
}