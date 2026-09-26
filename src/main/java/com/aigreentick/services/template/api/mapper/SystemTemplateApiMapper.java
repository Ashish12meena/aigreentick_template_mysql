package com.aigreentick.services.template.api.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.request.SaveSystemTemplateRequestDto;
import com.aigreentick.services.template.api.response.SystemTemplateDetailResponseDto;
import com.aigreentick.services.template.api.response.SystemTemplateSummaryResponseDto;
import com.aigreentick.services.template.api.response.common.PageResponse;
import com.aigreentick.services.template.application.dto.command.SaveSystemTemplateCommand;
import com.aigreentick.services.template.application.dto.result.SystemTemplateDetailResult;
import com.aigreentick.services.template.application.dto.result.SystemTemplateSummaryResult;

/**
 * Converts between the Template Library REST contract and the application's
 * command/result types.
 */
@Component
public class SystemTemplateApiMapper {

    public SaveSystemTemplateCommand toCommand(SaveSystemTemplateRequestDto request) {
        return SaveSystemTemplateCommand.builder()
                .description(request.getDescription())
                .sampleMediaUrl(request.getSampleMediaUrl())
                .templateData(request.getTemplate())
                .variables(request.getVariables())
                .active(request.isActive())
                .build();
    }

    public SystemTemplateSummaryResponseDto toSummaryResponse(SystemTemplateSummaryResult r) {
        return SystemTemplateSummaryResponseDto.builder()
                .id(r.getId())
                .name(r.getName())
                .language(r.getLanguage())
                .category(r.getCategory())
                .description(r.getDescription())
                .sampleMediaUrl(r.getSampleMediaUrl())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    public PageResponse<SystemTemplateSummaryResponseDto> toPageResponse(Page<SystemTemplateSummaryResult> page) {
        return PageResponse.from(page, this::toSummaryResponse);
    }

    public SystemTemplateDetailResponseDto toDetailResponse(SystemTemplateDetailResult r) {
        return SystemTemplateDetailResponseDto.builder()
                .id(r.getId())
                .description(r.getDescription())
                .sampleMediaUrl(r.getSampleMediaUrl())
                .active(r.isActive())
                .template(r.getTemplate())
                .variables(r.getVariables())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
