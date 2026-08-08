package com.aigreentick.services.template.application.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.aigreentick.services.template.application.dto.result.TemplateSummaryResult;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;

/**
 * Maps WhatsappTemplate entities to the application layer's own
 * {@link TemplateSummaryResult}. This lives in {@code application.mapper},
 * not {@code api.mapper} — the use case that lists templates must not
 * depend on anything under the {@code api} package.
 */
@Component
public class TemplateResultMapper {

    public TemplateSummaryResult toSummary(WhatsappTemplate t) {
        return TemplateSummaryResult.builder()
                .id(t.getId())
                .name(t.getName())
                .status(t.getStatus())
                .category(t.getCategory())
                .language(t.getLanguage())
                .metaTemplateId(t.getMetaTemplateId())
                .build();
    }

    public Page<TemplateSummaryResult> toPage(Page<WhatsappTemplate> page) {
        return page.map(this::toSummary);
    }
}
