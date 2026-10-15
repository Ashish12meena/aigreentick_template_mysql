package com.apargo.services.template.api.request;

import java.util.List;

import com.apargo.services.template.domain.enums.ButtonType;
import com.apargo.services.template.domain.enums.OtpType;
import com.fasterxml.jackson.annotation.JsonFormat;


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

    /**
     * A list for every button. A COPY_CODE example may also arrive as a single
     * string (Meta's own shape, also returned by sync); MetaTemplateJson
     * writes it back as a string for Meta.
     */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> example;

    private List<WhatsappTemplateButtonSupportedAppRequestDto> supportedApps;
}