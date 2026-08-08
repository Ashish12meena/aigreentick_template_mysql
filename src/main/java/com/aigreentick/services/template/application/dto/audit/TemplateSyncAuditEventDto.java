package com.aigreentick.services.template.application.dto.audit;

import com.aigreentick.services.template.domain.enums.TemplateAuditEventType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TemplateSyncAuditEventDto {

    private String eventId;
    private TemplateAuditEventType eventType;

    private Long orgId;
    private Long projectId;
    private String wabaId;

    // TEMPLATE_SYNC_COMPLETED
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer deletedCount;
    private Integer skippedCount;
    private Long durationMs;

    // TEMPLATE_SYNC_FAILED
    private String failureReason;

    private String actorType;
    private Long actorId;

    private Instant occurredAt;
}