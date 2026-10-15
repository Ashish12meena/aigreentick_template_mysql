package com.apargo.services.template.application.dto.command;

import java.util.List;

import com.apargo.services.template.api.request.BaseTemplateRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateVariablesRequestDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input to {@code EditTemplateUseCase}: new contents for a template that
 * already exists on Meta. Built by {@code api.mapper} from the same
 * {@code CreateTemplateRequestDto} used by create and draft update.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EditTemplateCommand {
    private Long templateId;
    private Long projectId;
    private Long organizationId;
    private BaseTemplateRequestDto templateData;
    private List<WhatsappTemplateVariablesRequestDto> variables;
}
