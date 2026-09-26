package com.aigreentick.services.template.application.dto;

import java.util.List;

import com.aigreentick.services.template.api.request.BaseTemplateRequestDto;
import com.aigreentick.services.template.api.request.WhatsappTemplateVariablesRequestDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The document stored in {@code system_templates.payload}: the create
 * request's {@code template} and {@code variables}, in the API's camelCase
 * shape. Reusing the request DTOs means a stored library template can be read
 * back and sent to {@code POST /api/v1/templates} without any conversion.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemTemplatePayload {
    private BaseTemplateRequestDto template;
    private List<WhatsappTemplateVariablesRequestDto> variables;
}
