package com.aigreentick.services.template.application.port.out;

import com.aigreentick.services.template.application.dto.audit.TemplateAuditEventDto;
import com.aigreentick.services.template.application.dto.audit.TemplateSyncAuditEventDto;

public interface TemplateAuditPort {

    void sendTemplateAudit(TemplateAuditEventDto event);

    void sendTemplateSyncAudit(TemplateSyncAuditEventDto event);
}