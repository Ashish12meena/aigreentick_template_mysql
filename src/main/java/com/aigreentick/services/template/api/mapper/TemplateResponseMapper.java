package com.aigreentick.services.template.api.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.response.TemplateResponseDto;
import com.aigreentick.services.template.application.dto.result.TemplateSummaryResult;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;

/**
 * Maps WhatsappTemplate entities to list-level response DTOs.
 * Keeps repository queries returning entities (not DTOs) for clean separation.
 */
@Component
public class TemplateResponseMapper {

    public TemplateResponseDto toListItem(WhatsappTemplate t) {
        return TemplateResponseDto.builder()
                .id(t.getId())
                .name(t.getName())
                .status(t.getStatus())
                .category(t.getCategory())
                .language(t.getLanguage())
                .metaTemplateId(t.getMetaTemplateId())
                .build();
    }

    public Page<TemplateResponseDto> toPage(Page<WhatsappTemplate> page) {
        return page.map(this::toListItem);
    }

    /** Converts a use-case result (application layer) into the REST response shape. */
    public TemplateResponseDto toResponseDto(TemplateSummaryResult r) {
        return TemplateResponseDto.builder()
                .id(r.getId())
                .name(r.getName())
                .status(r.getStatus())
                .category(r.getCategory())
                .language(r.getLanguage())
                .metaTemplateId(r.getMetaTemplateId())
                .build();
    }

    /** Converts a page of use-case results into a page of REST response DTOs. */
    public Page<TemplateResponseDto> toResponsePage(Page<TemplateSummaryResult> page) {
        return page.map(this::toResponseDto);
    }
}