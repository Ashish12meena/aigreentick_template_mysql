package com.aigreentick.services.template.api.dto.request.create;

import java.util.List;

import com.aigreentick.services.template.domain.enums.ButtonType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WhatsappTemplateCarouselButtonRequestDto {

    @NotNull(message = "carousel button type is required")
    private ButtonType type;

    @Size(max = 150, message = "carousel button text must not exceed 150 characters")
    private String text;

    @PositiveOrZero(message = "carousel button index must be zero or greater")
    private Integer index;

    @Size(max = 500, message = "carousel button url must not exceed 500 characters")
    private String url;

    private List<String> example;

    @Size(max = 30, message = "carousel button phoneNumber must not exceed 30 characters")
    private String phoneNumber;
}