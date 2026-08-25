package com.aigreentick.services.template.api.request;

import com.aigreentick.services.template.domain.enums.VariableComponentType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Maps a placeholder position to its label/value.
 *
 * Placeholder SEQUENCING (must start at 1, be contiguous, match the example
 * count) is a cross-field Meta rule and belongs to Phase 3. This class only
 * validates each entry in isolation.
 *
 * buttonIndex/cardIndex use -1 as a "not applicable" sentinel, so the lower
 * bound is -1 rather than 0.
 */
@Data
public class WhatsappTemplateVariablesRequestDto {

    @NotNull(message = "variable componentType is required")
    private VariableComponentType componentType;

    @NotNull(message = "variableIndex is required")
    @Positive(message = "variableIndex must be greater than zero")
    private Integer variableIndex;

    @Size(max = 255, message = "variable label must not exceed 255 characters")
    private String label;

    @Size(max = 500, message = "variable labelValue must not exceed 500 characters")
    private String labelValue;

    @Min(value = -1, message = "buttonIndex must be -1 (not applicable) or greater")
    private Integer buttonIndex = -1;

    @Min(value = -1, message = "cardIndex must be -1 (not applicable) or greater")
    private Integer cardIndex = -1;
}