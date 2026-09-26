package com.aigreentick.services.template.application.dto.command;

import java.util.List;

import com.aigreentick.services.template.api.request.BaseTemplateRequestDto;
import com.aigreentick.services.template.api.request.WhatsappTemplateVariablesRequestDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input to {@code CreateSystemTemplateUseCase} and
 * {@code UpdateSystemTemplateUseCase}: the full library entry. Update is a
 * full replace, so both use the same shape. Reuses the nested request DTOs
 * for the same reason as {@link CreateTemplateCommand}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SaveSystemTemplateCommand {
    private String description;
    private String sampleMediaUrl;
    private BaseTemplateRequestDto templateData;
    private List<WhatsappTemplateVariablesRequestDto> variables;
    private boolean active;
}
