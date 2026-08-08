package com.aigreentick.services.template.application.dto.command;

import java.util.List;

import com.aigreentick.services.template.api.dto.request.create.BaseTemplateRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateVariablesRequestDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input to {@code UpdateDraftTemplateUseCase} — built by
 * {@code api.mapper} from the incoming {@code CreateTemplateRequestDto}
 * (the same request shape is used for both create and update in the REST
 * contract; the application layer gets its own command type either way).
 *
 * See {@link CreateTemplateCommand} for the note on why
 * {@code templateData}/{@code variables} still reuse the nested request
 * DTO classes for now.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateDraftTemplateCommand {
    private Long templateId;
    private Long projectId;
    private Long organizationId;
    private BaseTemplateRequestDto templateData;
    private List<WhatsappTemplateVariablesRequestDto> variables;
    private String wabaId;
}
