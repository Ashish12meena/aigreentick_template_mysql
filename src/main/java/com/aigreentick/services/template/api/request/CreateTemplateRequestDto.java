package com.aigreentick.services.template.api.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Root request body for POST /api/v1/templates and PUT
 * /api/v1/templates/{id}/draft.
 *
 * VALIDATION NOTE: the {@code @Valid} on {@code template} is what makes the
 * controller's {@code @Valid} descend into the nested object graph. Without it,
 * Bean Validation stops at this class and every nested constraint is
 * unreachable.
 * Do not remove it.
 */
@Data
public class CreateTemplateRequestDto {

    @NotNull(message = "template is required")
    @Valid
    private BaseTemplateRequestDto template;

    private List<@Valid @NotNull(message = "variable entry must not be null") WhatsappTemplateVariablesRequestDto> variables;

    /**
     * NOTE: Lombok generates isDraft()/setDraft(), so the JSON property is
     * "draft", not "isDraft". Tracked separately as DTO-6.
     */
    private boolean isDraft = false;
}