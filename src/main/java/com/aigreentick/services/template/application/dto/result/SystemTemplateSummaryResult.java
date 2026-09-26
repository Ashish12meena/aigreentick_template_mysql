package com.aigreentick.services.template.application.dto.result;

import java.time.Instant;

import com.aigreentick.services.template.domain.enums.TemplateCategory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of the Template Library list: metadata only, no payload. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SystemTemplateSummaryResult {
    private Long id;
    private String name;
    private String language;
    private TemplateCategory category;
    private String description;
    private String sampleMediaUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
