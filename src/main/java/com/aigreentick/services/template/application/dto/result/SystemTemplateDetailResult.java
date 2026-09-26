package com.aigreentick.services.template.application.dto.result;

import java.time.Instant;
import java.util.List;

import com.aigreentick.services.template.api.request.BaseTemplateRequestDto;
import com.aigreentick.services.template.api.request.WhatsappTemplateVariablesRequestDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A full library entry with its payload parsed back into the create-request
 * types, so the caller receives exactly what it would send to create a
 * template.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SystemTemplateDetailResult {
    private Long id;
    private String description;
    private String sampleMediaUrl;
    private boolean active;
    private BaseTemplateRequestDto template;
    private List<WhatsappTemplateVariablesRequestDto> variables;
    private Instant createdAt;
    private Instant updatedAt;
}
