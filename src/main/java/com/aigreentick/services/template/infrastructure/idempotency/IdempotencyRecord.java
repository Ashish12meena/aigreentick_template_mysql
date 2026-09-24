package com.aigreentick.services.template.infrastructure.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One {@code X-Idempotency-Key} seen on a create/send endpoint, scoped to the
 * organization and project that sent it.
 *
 * <p>A web-layer concern, not a template: it lives in infrastructure next to
 * the interceptor that owns it, not in {@code domain.model}.
 */
@Entity
@Table(name = "api_idempotency_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_scope_key",
                columnNames = {"organization_id", "project_id", "idempotency_key"}),
        indexes = @Index(name = "idx_idempotency_created_at", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

    public enum State {
        IN_PROGRESS,
        COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    /** SHA-256 of method, path and body: detects a key reused for a different request. */
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private State state;

    @Column(name = "response_status")
    private Integer responseStatus;

    /** The standard response wrapper exactly as first returned. */
    @Column(name = "response_body", columnDefinition = "LONGTEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
