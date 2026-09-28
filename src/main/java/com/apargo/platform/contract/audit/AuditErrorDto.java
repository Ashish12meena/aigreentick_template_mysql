package com.apargo.platform.contract.audit;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Why a {@link AuditEventStatus#FAILURE} failed. Present only on failures.
 *
 * @param category  required
 * @param code      required: the service's own error code, e.g. {@code META_REJECTED}
 * @param message   user-safe text; never a stack trace, SQL, URL with
 *                  credentials, or an upstream body
 * @param details   small map of ids and enums, or {@code null}
 * @param reference the event's trace id, so support can find the logs
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditErrorDto(
        AuditErrorCategory category,
        String code,
        String message,
        Map<String, Object> details,
        String reference) {
}
