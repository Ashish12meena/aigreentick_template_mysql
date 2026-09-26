package com.aigreentick.services.template.api.response;

import java.time.Instant;

import com.aigreentick.services.template.domain.enums.TemplateCategory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One item of {@code GET /api/v1/template-library}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SystemTemplateSummaryResponseDto {
    private Long id;
    private String name;
    private String language;
    private TemplateCategory category;
    private String description;
    /** Preview-only media URL (our storage, not Meta). Absent for text-only templates. */
    private String sampleMediaUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
