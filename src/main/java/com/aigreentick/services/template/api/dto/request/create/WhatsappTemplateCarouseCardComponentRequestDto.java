package com.aigreentick.services.template.api.dto.request.create;

import java.util.List;

import com.aigreentick.services.template.domain.enums.CardComponentFormat;
import com.aigreentick.services.template.domain.enums.CardComponentType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WhatsappTemplateCarouseCardComponentRequestDto {

    @NotNull(message = "card component type is required")
    private CardComponentType type;

    private CardComponentFormat format;

    private String text;

    private WhatsappTemplateExampleRequestDto example;

    private List<@Valid WhatsappTemplateCarouselButtonRequestDto> buttons;
}