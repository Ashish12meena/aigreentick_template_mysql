package com.apargo.services.template.application.dto;

import com.apargo.platform.contract.audit.AuditActorDto;
import com.apargo.platform.contract.audit.AuditChannel;

/**
 * Who is acting, how, and in which request or job - everything an audit
 * event needs that does not come from the business data itself. Resolved by
 * the single {@code AuditContextProvider}.
 *
 * @param requestId {@code X-Request-Id}; {@code null} for scheduled jobs
 * @param traceId   32-hex backend trace id; always present
 * @param ip        client IP; {@code null} until the gateway provides it
 * @param userAgent client user agent; {@code null} until the gateway provides it
 */
public record AuditContext(
        AuditActorDto actor,
        AuditChannel channel,
        String requestId,
        String traceId,
        String ip,
        String userAgent,
        String sourceService,
        String environment) {
}
