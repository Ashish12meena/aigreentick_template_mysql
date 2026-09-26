package com.aigreentick.services.template.api.response;

import java.time.Instant;
import java.util.List;

import com.aigreentick.services.template.api.request.BaseTemplateRequestDto;
import com.aigreentick.services.template.api.request.WhatsappTemplateVariablesRequestDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A full Template Library entry. {@code template} and {@code variables} use
 * the create-template request shape on purpose: the client pre-fills its
 * create form from them and posts to {@code POST /api/v1/templates}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SystemTemplateDetailResponseDto {
    private Long id;
    private String description;
    /** Preview-only media URL (our storage, not Meta). Absent for text-only templates. */
    private String sampleMediaUrl;
    private boolean active;
    private BaseTemplateRequestDto template;
    private List<WhatsappTemplateVariablesRequestDto> variables;
    private Instant createdAt;
    private Instant updatedAt;
}
