package com.aigreentick.services.template.api.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * One card in a CAROUSEL component.
 * Card-count limits and cross-card homogeneity (all cards must share the same
 * component structure, media format, and button layout) are Meta rules — Phase 3.
 */
@Data
public class WhatsappTemplateCarouselCardRequestDto {

    @NotEmpty(message = "carousel card must contain at least one component")
    private List<@Valid @NotNull(message = "card component must not be null") WhatsappTemplateCarouseCardComponentRequestDto> components;
}