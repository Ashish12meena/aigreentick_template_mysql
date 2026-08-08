package com.aigreentick.services.template.application.dto.command;

import java.util.List;

import com.aigreentick.services.template.api.dto.request.create.BaseTemplateRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateVariablesRequestDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input to {@code CreateTemplateUseCase} — the application layer's own
 * boundary type, built by {@code api.mapper} from the incoming
 * {@code CreateTemplateRequestDto}. The use case never imports the API
 * request DTO directly; it only knows about this command.
 *
 * NOTE (intentional interim state): {@code templateData} and
 * {@code variables} still reuse the existing nested request DTO classes
 * ({@code BaseTemplateRequestDto}, {@code WhatsappTemplateVariablesRequestDto})
 * rather than a fully-parallel application-owned tree. Those nested classes
 * carry Bean Validation annotations (framework-agnostic, not Spring-specific),
 * so reusing them here is a pragmatic middle ground — full separation would
 * mean duplicating the entire nested component/button/carousel tree, which
 * is a larger follow-up migration, not a blocker for fixing the top-level
 * layering violation this command exists to solve.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateTemplateCommand {
    private BaseTemplateRequestDto templateData;
    private List<WhatsappTemplateVariablesRequestDto> variables;
    private String wabaId;
    private Long projectId;
    private Long organizationId;
    private boolean draft;
}
