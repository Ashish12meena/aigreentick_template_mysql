package com.apargo.services.template.application.port.out;

import com.apargo.services.template.application.dto.AuditContext;

/**
 * The one owner of actor, channel and request/trace context for audit
 * events (Audit Producer Guide §6). Use cases never read headers or the MDC
 * for this; {@code TemplateAuditEvents} asks here.
 */
public interface AuditContextProvider {

    /** Context of the current thread's request or job. Never throws, never null. */
    AuditContext current();

    /** The request's organization; {@code null} outside a tenant-scoped request. Never throws. */
    default Long organizationId() {
        return null;
    }

    /** The request's project; {@code null} when the request names none. Never throws. */
    default Long projectId() {
        return null;
    }
}
