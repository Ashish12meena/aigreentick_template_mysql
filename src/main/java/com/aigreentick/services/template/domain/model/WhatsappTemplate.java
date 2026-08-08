package com.aigreentick.services.template.domain.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateQualityRating;
import com.aigreentick.services.template.domain.enums.TemplateStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "whatsapp_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_waba_template", columnNames = { "waba_id", "name", "language" })
}, indexes = {
        @Index(name = "idx_project_status", columnList = "project_id, status"),
        @Index(name = "idx_waba_id", columnList = "waba_id")
})
@SQLDelete(sql = "UPDATE whatsapp_templates SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Data
public class WhatsappTemplate {

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
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WhatsappTemplateComponent> components = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WhatsappTemplateVariable> variables = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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