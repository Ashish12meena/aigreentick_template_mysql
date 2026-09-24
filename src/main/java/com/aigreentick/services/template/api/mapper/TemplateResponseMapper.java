package com.aigreentick.services.template.api.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.response.TemplateResponseDto;
import com.aigreentick.services.template.api.response.common.PageResponse;
import com.aigreentick.services.template.application.dto.result.TemplateSummaryResult;

/**
 * Maps list-level use-case results to the REST response shape.
 */
@Component
public class TemplateResponseMapper {

    /** Converts a use-case result (application layer) into the REST response shape. */
    public TemplateResponseDto toResponseDto(TemplateSummaryResult r) {
        return TemplateResponseDto.builder()
                .id(r.getId())
                .name(r.getName())
                .status(r.getStatus())
                .category(r.getCategory())
                .language(r.getLanguage())
                .metaTemplateId(r.getMetaTemplateId())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    /** Converts a page of use-case results into the standard {@code {items, pagination}} list payload. */
    public PageResponse<TemplateResponseDto> toPageResponse(Page<TemplateSummaryResult> page) {
        return PageResponse.from(page, this::toResponseDto);
    }
}
