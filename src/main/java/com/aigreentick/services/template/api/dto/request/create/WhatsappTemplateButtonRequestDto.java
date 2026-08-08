package com.aigreentick.services.template.api.dto.request.create;

import java.util.List;

import com.aigreentick.services.template.domain.enums.ButtonType;
import com.aigreentick.services.template.domain.enums.OtpType;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WhatsappTemplateButtonRequestDto {

    @NotNull(message = "button type is required")
    private ButtonType type;

    private OtpType otpType;

    @Size(max = 30, message = "button phoneNumber must not exceed 30 characters")
    private String phoneNumber;

    @Size(max = 150, message = "button text must not exceed 150 characters")
    private String text;

    @PositiveOrZero(message = "button index must be zero or greater")
    private Integer index;

    @Size(max = 500, message = "button url must not exceed 500 characters")
    private String url;

    private String autofillText;

    private List<String> example;

    private List<WhatsappTemplateButtonSupportedAppRequestDto> supportedApps;
}