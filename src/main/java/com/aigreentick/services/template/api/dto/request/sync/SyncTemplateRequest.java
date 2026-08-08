package com.aigreentick.services.template.api.dto.request.sync;

import java.util.List;

import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateComponentRequestDto;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;


@Data
public class SyncTemplateRequest {
    private String name;

    private String category;

    private String language;

    private TemplateStatus status;  // Pending, Approved, Rejected

    private String rejectionReason;

    private String previousCategory;

    private String parameterFormat;

    @JsonProperty("id")
    private String metaTemplateId;


    private List<WhatsappTemplateComponentRequestDto> components;

}
