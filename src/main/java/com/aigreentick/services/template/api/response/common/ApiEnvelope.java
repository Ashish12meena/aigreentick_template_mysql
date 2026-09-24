package com.aigreentick.services.template.api.response.common;

import com.aigreentick.services.template.common.constant.LogKeys;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * The single response wrapper for every JSON body this API returns, success
 * or error (API Standard §4). Frontend type: {@code ApiResponse<T>}.
 *
 * <h2>Invariants (enforced by construction)</h2>
 * <ul>
 *   <li>{@code status} equals the HTTP status; {@code success} is
 *       {@code true} exactly when it is 2xx. Both are derived from the one
 *       {@link HttpStatus} passed to the factory, which the caller also uses
 *       for the {@code ResponseEntity}.</li>
 *   <li>Success always carries {@code code = "SUCCESS"} and
 *       {@code errors = []}; error always carries {@code data = null}.</li>
 *   <li>{@code meta.requestId} is the value echoed in {@code X-Request-Id}.</li>
 * </ul>
 *
 * <h2>Why {@code @JsonInclude(ALWAYS)}</h2>
 *
 * The service sets {@code default-property-inclusion: non_null} globally.
 * The standard requires {@code data: null} on errors and {@code errors: []}
 * on success, so the wrapper opts out; payload DTOs inside {@code data} keep
 * the global setting.
 *
 * <p>Named {@code ApiEnvelope} rather than {@code ApiResponse} only to avoid
 * clashing with springdoc's {@code @ApiResponse} annotation in controllers.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"success", "status", "code", "message", "data", "errors", "meta"})
@Schema(description = "Standard response wrapper used by every JSON endpoint")
public record ApiEnvelope<T>(
        @Schema(description = "true only when status is 2xx", example = "true")
        boolean success,

        @Schema(description = "Always equal to the HTTP status code", example = "200")
        int status,

        @Schema(description = "SUCCESS, or an error code such as TEMPLATE_NOT_FOUND", example = "SUCCESS")
        String code,

        @Schema(description = "Short human-readable text; may be shown to the user")
        String message,

        @Schema(description = "Endpoint-specific payload; null on error. Lists are {items, pagination}.")
        T data,

        @Schema(description = "Field errors; empty unless validation failed")
        List<ApiFieldError> errors,

        Meta meta) {

    /** Code carried by every successful response. */
    public static final String SUCCESS_CODE = "SUCCESS";

    public static <T> ApiEnvelope<T> success(HttpStatus status, String message, T data) {
        if (!status.is2xxSuccessful()) {
            throw new IllegalArgumentException("Success wrapper requires a 2xx status, got " + status);
        }
        return new ApiEnvelope<>(true, status.value(), SUCCESS_CODE, message, data, List.of(), Meta.now(null));
    }

    public static ApiEnvelope<Void> error(HttpStatus status, String code, String message,
                                          List<ApiFieldError> errors, String path) {
        if (status.is2xxSuccessful()) {
            throw new IllegalArgumentException("Error wrapper requires a 4xx/5xx status, got " + status);
        }
        return new ApiEnvelope<>(false, status.value(), code, message, null,
                errors == null ? List.of() : List.copyOf(errors), Meta.now(path));
    }

    /** Request metadata. {@code path} is present on errors only. */
    @JsonPropertyOrder({"requestId", "timestamp", "path"})
    @Schema(description = "Request metadata")
    public record Meta(
            @Schema(description = "Same value as the X-Request-Id response header")
            String requestId,

            @Schema(description = "ISO-8601 UTC time the response was produced", example = "2026-01-15T10:30:00Z")
            Instant timestamp,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Request path; present on errors only")
            String path) {

        static Meta now(String path) {
            return new Meta(MDC.get(LogKeys.REQUEST_ID), Instant.now(), path);
        }
    }
}
