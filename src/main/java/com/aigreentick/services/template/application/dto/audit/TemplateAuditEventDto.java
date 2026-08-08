package com.aigreentick.services.template.application.dto.audit;

import com.aigreentick.services.template.domain.enums.TemplateAuditEventType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TemplateAuditEventDto {

    private String eventId;
    private TemplateAuditEventType eventType;

    private Long orgId;
    private Long projectId;
    private String wabaId;
    private Long templateId;

    private String fromStatus;
    private String toStatus;

    // TEMPLATE_CREATED
    private Boolean isDraft;

    // TEMPLATE_REJECTED / TEMPLATE_PAUSED
    private String rejectionReason;

    // TEMPLATE_CATEGORY_CHANGED
    private String previousCategory;
    private String newCategory;

    // TEMPLATE_DELETED
    private Boolean deletedFromMeta;

    // TEMPLATE_BULK_DELETED
    private Integer deletedCount;

    private String actorType;
    private Long actorId;

    private Instant occurredAt;
}