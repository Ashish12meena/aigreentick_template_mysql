package com.aigreentick.services.template.domain.repository;

import java.time.Instant;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;

/**
 * Write operations for WhatsApp templates.
 * Soft-deletes and bulk mutations live here — reads go to QueryRepository.
 */
@Repository
public interface WhatsappTemplateCommandRepository extends JpaRepository<WhatsappTemplate, Long> {

    // ── Single soft-delete ──

    @Modifying
    @Query("""
            UPDATE WhatsappTemplate t
            SET t.deletedAt = :deletedAt
            WHERE t.id = :id
              AND t.projectId = :projectId
              AND t.deletedAt IS NULL
            """)
    int softDeleteById(
            @Param("id") Long id,
            @Param("projectId") Long projectId,
            @Param("deletedAt") Instant deletedAt);

    // ── Bulk soft-delete by project ──

    @Modifying
    @Query("""
            UPDATE WhatsappTemplate t
            SET t.deletedAt = :deletedAt
            WHERE t.projectId = :projectId
              AND t.deletedAt IS NULL
            """)
    int softDeleteAllByProject(
            @Param("projectId") Long projectId,
            @Param("deletedAt") Instant deletedAt);

    // ── Sync: soft-delete stale templates by meta IDs ──

    @Modifying
    @Query("""
            UPDATE WhatsappTemplate t
            SET t.deletedAt = :deletedAt
            WHERE t.metaTemplateId IN :metaIds
              AND t.projectId = :projectId
              AND t.status <> 'DRAFT'
              AND t.deletedAt IS NULL
            """)
    int softDeleteStaleByMetaIds(
            @Param("metaIds") Set<String> metaIds,
            @Param("projectId") Long projectId,
            @Param("deletedAt") Instant deletedAt);

    // ── Guarded status transition ──

    /**
     * Moves a template from {@code from} to {@code to} only if it is still in
     * {@code from}. Returns the number of rows changed (0 or 1), so two
     * concurrent submits of the same draft cannot both reach Meta.
     *
     * <p>Bulk JPQL updates bypass {@code @PreUpdate}, so {@code updatedAt} is
     * set here explicitly — the stuck-SUBMITTED reconciler relies on it.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE WhatsappTemplate t
            SET t.status = :toStatus,
                t.updatedAt = :now
            WHERE t.id = :id
              AND t.status = :fromStatus
              AND t.deletedAt IS NULL
            """)
    int transitionStatus(
            @Param("id") Long id,
            @Param("fromStatus") TemplateStatus from,
            @Param("toStatus") TemplateStatus to,
            @Param("now") Instant now);
}
