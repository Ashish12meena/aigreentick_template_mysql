package com.aigreentick.services.template.application.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.request.BaseTemplateRequestDto;
import com.aigreentick.services.template.application.dto.SystemTemplatePayload;
import com.aigreentick.services.template.application.dto.command.SaveSystemTemplateCommand;
import com.aigreentick.services.template.application.dto.result.SystemTemplateDetailResult;
import com.aigreentick.services.template.application.dto.result.SystemTemplateSummaryResult;
import com.aigreentick.services.template.common.util.helper.JsonHelper;
import com.aigreentick.services.template.domain.model.SystemTemplate;

/**
 * Converts between {@link SystemTemplate} rows and the application's
 * command/result types.
 *
 * <p>The payload is written with {@link JsonHelper#serialize} (camelCase,
 * the API's own naming) and read back with {@link JsonHelper#deserialize},
 * never with the snake_case Meta helpers: it is the create-request shape,
 * not a Meta payload.
 */
@Component
public class SystemTemplateMapper {

    /**
     * Copies a command onto a new or existing row. The indexed columns are
     * taken from the template itself, so they can never disagree with the
     * stored payload.
     */
    public void apply(SaveSystemTemplateCommand command, SystemTemplate target) {
        BaseTemplateRequestDto template = command.getTemplateData();
        target.setName(template.getName());
        target.setLanguage(template.getLanguage());
        target.setCategory(template.getCategory());
        target.setDescription(command.getDescription());
        target.setSampleMediaUrl(command.getSampleMediaUrl());
        target.setActive(command.isActive());
        target.setPayload(JsonHelper.serialize(
                new SystemTemplatePayload(template, command.getVariables())));
    }

    public SystemTemplateSummaryResult toSummary(SystemTemplate s) {
        return SystemTemplateSummaryResult.builder()
                .id(s.getId())
                .name(s.getName())
                .language(s.getLanguage())
                .category(s.getCategory())
                .description(s.getDescription())
                .sampleMediaUrl(s.getSampleMediaUrl())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    public Page<SystemTemplateSummaryResult> toPage(Page<SystemTemplate> page) {
        return page.map(this::toSummary);
    }

    public SystemTemplateDetailResult toDetail(SystemTemplate s) {
        SystemTemplatePayload payload = JsonHelper.deserialize(s.getPayload(), SystemTemplatePayload.class);
        return SystemTemplateDetailResult.builder()
                .id(s.getId())
                .description(s.getDescription())
                .sampleMediaUrl(s.getSampleMediaUrl())
                .active(s.isActive())
                .template(payload.getTemplate())
                .variables(payload.getVariables())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
