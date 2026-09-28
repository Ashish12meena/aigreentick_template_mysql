package com.apargo.services.template.application.audit;

import com.apargo.services.template.domain.enums.TemplateCategory;
import com.apargo.services.template.domain.model.SystemTemplate;

/**
 * The audited fields of a Template Library entry at one moment. The JSON
 * {@code payload} is deliberately not audited field by field: it is the whole
 * template body, and the guide forbids full bodies in events. A payload
 * change is reported as the single flag {@code payloadChanged} in metadata.
 */
public record SystemTemplateAuditSnapshot(
        Long id,
        String name,
        String language,
        TemplateCategory category,
        String description,
        String sampleMediaUrl,
        boolean active,
        String payload) {

    public static SystemTemplateAuditSnapshot of(SystemTemplate template) {
        return new SystemTemplateAuditSnapshot(
                template.getId(),
                template.getName(),
                template.getLanguage(),
                template.getCategory(),
                template.getDescription(),
                template.getSampleMediaUrl(),
                template.isActive(),
                template.getPayload());
    }
}
