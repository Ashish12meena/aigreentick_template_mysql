package com.aigreentick.services.template.domain.model;

import java.time.Instant;

import com.aigreentick.services.template.domain.enums.TemplateCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A Template Library entry: a predefined template owned by the system, not by
 * any organization or project. Users copy it into their own project through
 * the normal create API; this row is never submitted to Meta.
 *
 * <p>The whole template lives in {@link #payload} as camelCase JSON in the
 * same shape as the create request ({@code {template, variables}}), so it is
 * read back with the existing request DTOs. {@link #name}, {@link #language}
 * and {@link #category} are copies of {@code payload.template.*}, kept as
 * columns only for listing, filtering and uniqueness; they are always set
 * from the payload, never on their own.
 */
@Entity
@Table(name = "system_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_template_name_lang", columnNames = { "name", "language" })
}, indexes = {
        @Index(name = "idx_system_template_category", columnList = "is_active, category")
})
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class SystemTemplate {

    @ToString.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Include
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @ToString.Include
    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private TemplateCategory category;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Public URL (our own storage, not Meta) of a sample header image / video /
     * document, used only to preview the entry in the library UI. Never sent to
     * Meta: a user supplies their own media, and its Meta handle, on create.
     */
    @Column(name = "sample_media_url", length = 500)
    private String sampleMediaUrl;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    /** Inactive entries are hidden from the public library but kept for reference. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
