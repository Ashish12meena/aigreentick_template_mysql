package com.aigreentick.services.template.api.dto.request.create;

import java.util.List;

import com.aigreentick.services.template.domain.enums.ComponentFormat;
import com.aigreentick.services.template.domain.enums.ComponentType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A single template component (HEADER / BODY / FOOTER / BUTTONS / CAROUSEL / LTO).
 *
 * {@code format} is now a typed enum rather than a raw String. An unrecognised
 * value is rejected by Jackson at bind time (400) instead of blowing up as a
 * 500 inside the mapper's valueOf() call.
 *
 * The 4096-char ceiling on {@code text} is a denial-of-service guard only.
 * Meta's real per-component limits (BODY 1024, HEADER 60, FOOTER 60) are
 * component-type-dependent and are enforced by the Meta rule engine (Phase 3).
 */
@Data
public class WhatsappTemplateComponentRequestDto {

    @NotNull(message = "component type is required")
    private ComponentType type;

    private ComponentFormat format;

    @Size(max = 4096, message = "component text must not exceed 4096 characters")
    private String text;

    private Boolean addSecurityRecommendation;

    private Integer codeExpirationMinutes;

    private List<@Valid @NotNull(message = "button must not be null") WhatsappTemplateButtonRequestDto> buttons;

    private List<@Valid @NotNull(message = "carousel card must not be null") WhatsappTemplateCarouselCardRequestDto> cards;

    @Valid
    private WhatsappTemplateExampleRequestDto example;
}