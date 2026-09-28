package com.apargo.platform.contract.access;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One access event, published by the auth service and the gateway only, to
 * the access topic ({@code audit.topics.access}, default
 * {@code platform.access.events}). Business services publish
 * {@code AuditEventDto} instead.
 *
 * <p>Same conventions as the audit contract: camelCase JSON, UTC ISO-8601
 * instants, absent optional fields omitted, UUIDv7 {@code eventId}, and never
 * a password, token, secret, API key or stack trace. In particular the
 * attempted login name is not carried: on a failed sign-in it is often a
 * mistyped password.
 *
 * @param eventId       required, UUIDv7
 * @param schemaVersion required, {@code EventSchemaVersion.ACCESS}
 * @param sourceService required, e.g. {@code auth-service}
 * @param environment   required: development / staging / production
 * @param eventType     required, e.g. {@code LOGIN_SUCCEEDED}, {@code LOGIN_FAILED},
 *                      {@code TOKEN_REFRESHED}, {@code LOGGED_OUT}, {@code ACCESS_DENIED}
 * @param status        required
 * @param orgId         when the principal belongs to an organization
 * @param actor         required
 * @param failureCode   service error code on FAILURE, e.g. {@code INVALID_CREDENTIALS}
 * @param requestId     from {@code X-Request-Id}
 * @param traceId       required
 * @param ip            client IP
 * @param userAgent     client user agent
 * @param occurredAt    required
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"eventId", "schemaVersion", "sourceService", "environment", "eventType", "status",
        "orgId", "actor", "failureCode", "requestId", "traceId", "ip", "userAgent", "occurredAt"})
public record AccessEventDto(
        String eventId,
        Integer schemaVersion,
        String sourceService,
        String environment,
        String eventType,
        AccessEventStatus status,
        Long orgId,
        AccessActorDto actor,
        String failureCode,
        String requestId,
        String traceId,
        String ip,
        String userAgent,
        Instant occurredAt) {
}
