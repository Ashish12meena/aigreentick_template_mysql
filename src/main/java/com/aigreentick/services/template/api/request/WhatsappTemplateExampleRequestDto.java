package com.aigreentick.services.template.api.request;

import java.util.List;

import lombok.Data;

@Data
public class WhatsappTemplateExampleRequestDto {
    private List<String> headerHandle;
    
    private List<String> headerText;

    List<List<String>> bodyText;
}
