package com.aigreentick.services.template.api.mapper;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.request.CreateTemplateRequestDto;
import com.aigreentick.services.template.api.response.TemplateResponseDto;
import com.aigreentick.services.template.application.dto.command.CreateTemplateCommand;
import com.aigreentick.services.template.application.dto.command.UpdateDraftTemplateCommand;
import com.aigreentick.services.template.application.dto.result.TemplateResult;

/**
 * The ONLY place that converts between the REST contract
 * ({@code api.dto.request.create.CreateTemplateRequestDto} /
 * {@code api.dto.response.TemplateResponseDto}) and the application
 * layer's own boundary types
 * ({@code application.dto.command.CreateTemplateCommand} /
 * {@code application.dto.result.TemplateResult}).
 *
 * This mapper is allowed to depend on both {@code api.dto} and
 * {@code application.dto} — that dependency direction (api → application)
 * is the one direction RULES.md allows. The use case itself never sees
 * {@code CreateTemplateRequestDto} or {@code TemplateResponseDto}.
 */
@Component
public class CreateTemplateApiMapper {

    public CreateTemplateCommand toCommand(CreateTemplateRequestDto request, Long projectId, Long organizationId, String wabaId) {
        return CreateTemplateCommand.builder()
                .templateData(request.getTemplate())
                .variables(request.getVariables())
                .wabaId(wabaId)
                .projectId(projectId)
                .organizationId(organizationId)
                .draft(request.isDraft())
                .build();
    }

    public UpdateDraftTemplateCommand toUpdateCommand(
            CreateTemplateRequestDto request, Long templateId, Long projectId, Long organizationId,String wabaId) {
        return UpdateDraftTemplateCommand.builder()
                .templateId(templateId)
                .projectId(projectId)
                .organizationId(organizationId)
                .templateData(request.getTemplate())
                .variables(request.getVariables())
                .wabaId(wabaId)
                .build();
    }

    public TemplateResponseDto toResponseDto(TemplateResult result) {
        return TemplateResponseDto.builder()
                .id(result.getId())
                .name(result.getName())
                .status(result.getStatus())
                .category(result.getCategory())
                .language(result.getLanguage())
                .metaTemplateId(result.getMetaTemplateId())
                .errorMessage(result.getErrorMessage())
                .errorPayload(result.getErrorPayload())
                .build();
    }
}
