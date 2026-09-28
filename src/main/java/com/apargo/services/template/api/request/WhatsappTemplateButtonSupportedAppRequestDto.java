package com.apargo.services.template.api.request;
import lombok.Data;

@Data
public class WhatsappTemplateButtonSupportedAppRequestDto {
    private String packageName;

    private String signatureHash;
}
