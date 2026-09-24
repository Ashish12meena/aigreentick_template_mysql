package com.aigreentick.services.template.infrastructure.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Query("""
            SELECT r FROM IdempotencyRecord r
            WHERE r.organizationId = :organizationId
              AND r.projectId = :projectId
              AND r.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord> findByScope(
            @Param("organizationId") Long organizationId,
            @Param("projectId") Long projectId,
            @Param("idempotencyKey") String idempotencyKey);

    @Modifying
    @Query("""
            UPDATE IdempotencyRecord r
            SET r.state = :state,
                r.responseStatus = :responseStatus,
                r.responseBody = :responseBody,
                r.completedAt = :completedAt
            WHERE r.id = :id
            """)
    int markCompleted(
            @Param("id") Long id,
            @Param("state") IdempotencyRecord.State state,
            @Param("responseStatus") int responseStatus,
            @Param("responseBody") String responseBody,
            @Param("completedAt") Instant completedAt);

    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
