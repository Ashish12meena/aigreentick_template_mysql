package com.aigreentick.services.template.api.dto.request.create;
import lombok.Data;

@Data
public class WhatsappTemplateButtonSupportedAppRequestDto {
    private String packageName;

    private String signatureHash;
}
