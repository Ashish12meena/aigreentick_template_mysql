package com.aigreentick.services.template.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateQualityRating;
import com.aigreentick.services.template.domain.enums.TemplateStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "whatsapp_templates", uniqueConstraints = {
        /*
         * One LIVE template per (waba, name, language). live_flag is 1 only for
         * rows that exist (or may exist) on Meta and NULL otherwise; MySQL lets
         * any number of rows share a unique key that contains NULL. So DRAFT,
         * FAILED and soft-deleted rows never block a name - a failed template
         * can be fixed and recreated under the same name while its failed row
         * (and Meta's error in meta_response) stays as history.
         */
        @UniqueConstraint(name = "uk_waba_template_live", columnNames = { "waba_id", "name", "language", "live_flag" })
}, indexes = {
        @Index(name = "idx_project_status", columnList = "project_id, status"),
        @Index(name = "idx_waba_id", columnList = "waba_id"),
        @Index(name = "idx_status_updated", columnList = "status, updated_at")
})
@SQLDelete(sql = "UPDATE whatsapp_templates SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class WhatsappTemplate {

    @ToString.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "waba_id", nullable = false, length = 255)
    private String wabaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_rating", nullable = false)
    private TemplateQualityRating qualityRating = TemplateQualityRating.UNKNOWN;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private TemplateCategory category;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TemplateStatus status = TemplateStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_category")
    private TemplateCategory previousCategory;

    @Column(name = "meta_template_id", length = 150)
    private String metaTemplateId;

    @Column(name = "submission_payload", columnDefinition = "JSON")
    private String submissionPayload;

    @Column(name = "meta_response", columnDefinition = "JSON")
    private String metaResponse;

    @Column(name = "meta_status_raw", length = 64)
    private String metaStatusRaw;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Computed by MySQL - never written by the application. 1 when the row is
     * live (not soft-deleted, status not DRAFT/FAILED), otherwise NULL.
     * Backs {@code uk_waba_template_live}; see the class-level note.
     *
     * <p>The expression must stay identical to db/migration/template.sql.
     */
    @Column(name = "live_flag", insertable = false, updatable = false,
            columnDefinition = "TINYINT GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL "
                    + "AND status NOT IN ('DRAFT','FAILED') THEN 1 ELSE NULL END) STORED")
    private Byte liveFlag;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WhatsappTemplateComponent> components = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WhatsappTemplateVariable> variables = new ArrayList<>();

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

    // Helper methods for bidirectional relationship management
    public void addComponent(WhatsappTemplateComponent component) {
        components.add(component);
        component.setTemplate(this);
    }

    public void removeComponent(WhatsappTemplateComponent component) {
        components.remove(component);
        component.setTemplate(null);
    }

    public void addVariable(WhatsappTemplateVariable variable) {
        variables.add(variable);
        variable.setTemplate(this);
    }

    public void removeVariable(WhatsappTemplateVariable variable) {
        variables.remove(variable);
        variable.setTemplate(null);
    }
}