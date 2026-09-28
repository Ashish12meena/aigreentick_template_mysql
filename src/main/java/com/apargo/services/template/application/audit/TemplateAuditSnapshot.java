package com.apargo.services.template.application.audit;

import com.apargo.services.template.domain.enums.TemplateCategory;
import com.apargo.services.template.domain.enums.TemplateStatus;
import com.apargo.services.template.domain.model.WhatsappTemplate;

/**
 * The audited fields of a template at one moment. Taken before a change so
 * {@code TemplateAuditEvents} can report old and new values; entities are
 * mutated in place, so the "before" state is otherwise lost.
 */
public record TemplateAuditSnapshot(
        Long id,
        Long organizationId,
        Long projectId,
        String wabaId,
        String name,
        String language,
        TemplateStatus status,
        TemplateCategory category,
        String metaTemplateId,
        String rejectionReason) {

    public static TemplateAuditSnapshot of(WhatsappTemplate template) {
        return new TemplateAuditSnapshot(
                template.getId(),
                template.getOrganizationId(),
                template.getProjectId(),
                template.getWabaId(),
                template.getName(),
                template.getLanguage(),
                template.getStatus(),
                template.getCategory(),
                template.getMetaTemplateId(),
                template.getRejectionReason());
    }
}
