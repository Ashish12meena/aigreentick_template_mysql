package com.aigreentick.services.template.application.dto.result;

import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight per-row shape used by {@code GetTemplateUseCase.list(...)}.
 * Kept separate from {@link TemplateResult} because a list row never
 * carries error fields — it's a smaller, purpose-specific shape.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TemplateSummaryResult {
    private Long id;
    private String name;
    private TemplateStatus status;
    private TemplateCategory category;
    private String language;
    private String metaTemplateId;
}
