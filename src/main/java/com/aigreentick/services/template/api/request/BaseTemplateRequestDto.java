package com.aigreentick.services.template.api.request;

import java.util.List;

import com.aigreentick.services.template.domain.enums.TemplateCategory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Core template definition.
 *
 * Meta's own limits (name up to 512 chars, lowercase
 * snake_case naming rule, per-component text limits) are deliberately NOT
 * enforced here — they belong to the Meta rule engine (Phase 3).
 */
@Data
public class BaseTemplateRequestDto {

    @NotBlank(message = "template name is required")    
    @Size(max = 150, message = "template name must not exceed 150 characters")
    private String name;

    @NotBlank(message = "template language is required")
    @Size(max = 10, message = "template language must not exceed 10 characters")
    private String language;

    @NotNull(message = "template category is required")
    private TemplateCategory category;

    @NotEmpty(message = "template must contain at least one component")
    private List<@Valid @NotNull(message = "component must not be null") WhatsappTemplateComponentRequestDto> components;
}