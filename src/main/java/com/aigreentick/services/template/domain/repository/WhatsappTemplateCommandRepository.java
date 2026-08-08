package com.aigreentick.services.template.domain.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
            SET t.deletedAt = CURRENT_TIMESTAMP
            WHERE t.id = :id
              AND t.projectId = :projectId
              AND t.deletedAt IS NULL
            """)
    int softDeleteById(
            @Param("id") Long id,
            @Param("projectId") Long projectId);

    // ── Bulk soft-delete by project ──

    @Modifying
    @Query("""
            UPDATE WhatsappTemplate t
            SET t.deletedAt = CURRENT_TIMESTAMP
            WHERE t.projectId = :projectId
              AND t.deletedAt IS NULL
            """)
    int softDeleteAllByProject(@Param("projectId") Long projectId);

    // ── Sync: soft-delete stale templates by meta IDs ──

    @Modifying
    @Query("""
            UPDATE WhatsappTemplate t
            SET t.deletedAt = CURRENT_TIMESTAMP
            WHERE t.metaTemplateId IN :metaIds
              AND t.projectId = :projectId
              AND t.status <> 'DRAFT'
              AND t.deletedAt IS NULL
            """)
    int softDeleteStaleByMetaIds(
            @Param("metaIds") Set<String> metaIds,
            @Param("projectId") Long projectId);
}