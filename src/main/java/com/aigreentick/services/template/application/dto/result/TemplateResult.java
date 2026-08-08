package com.aigreentick.services.template.application.dto.result;

import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output of {@code CreateTemplateUseCase} / {@code UpdateDraftTemplateUseCase}
 * / {@code SubmitDraftToMetaUseCase} — this is the application layer's own
 * result shape. It is deliberately NOT the same class as
 * {@code api.dto.response.TemplateResponseDto}: the API layer maps this to
 * whatever shape the REST contract needs, so this class is free to evolve
 * with the use case rather than with the HTTP response body.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TemplateResult {
    private Long id;
    private String name;
    private TemplateStatus status;
    private TemplateCategory category;
    private String language;
    private String metaTemplateId;
    private String errorMessage;
    private JsonNode errorPayload;
}
