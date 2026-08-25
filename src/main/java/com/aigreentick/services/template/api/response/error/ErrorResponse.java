package com.aigreentick.services.template.api.response.error;

import com.aigreentick.services.template.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * The single error envelope for every failure this API returns, whether it
 * originates in a controller, the exception handler, or the internal auth
 * filter.
 *
 * <h2>The shape is deliberately unchanged</h2>
 *
 * {@code status}, {@code code}, {@code message}, {@code path},
 * {@code timestamp} and {@code fieldErrors} all keep the exact names, types
 * and meanings they had before. {@link #errorCode} and {@link #traceId} are
 * <em>additive</em> — a consumer that ignores unknown fields (the Messaging
 * Service does; so does this service's own client config) sees no difference
 * at all.
 *
 * <p>That constraint is the whole reason this class was extended rather than
 * replaced by a cleaner-looking envelope. Error bodies are part of the API
 * contract just as much as success bodies, and {@code 404} handling on the
 * message-send path depends on this shape.
 *
 * <h2>Why errorCode exists alongside code</h2>
 *
 * {@code code} is the numeric HTTP status, which is far too coarse to branch
 * on: "template does not exist" and "no handler mapped to this path" are both
 * {@code 404}, and the first means "stop retrying" while the second means
 * "this service was deployed wrong". {@link #errorCode} separates them.
 * {@code message} cannot be used for this — it is prose written for humans
 * and is free to be reworded at any time.
 *
 * <h2>Serialization</h2>
 *
 * This service applies {@code SNAKE_CASE} globally, so these fields go out as
 * {@code error_code}, {@code trace_id}, {@code field_errors}. That naming is
 * a frozen contract; see {@code application.yaml}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response envelope")
public class ErrorResponse {

    @Schema(description = "Always the literal \"ERROR\" - lets a client branch before parsing", example = "ERROR")
    private String status;

    @Schema(description = "HTTP status code", example = "404")
    private int code;

    @Schema(description = "Stable, machine-readable classification - branch on this, not on message",
            example = "RESOURCE_NOT_FOUND")
    private ErrorCode errorCode;

    @Schema(description = "Human-readable explanation. Wording is not part of the contract.")
    private String message;

    @Schema(description = "Request path that produced the error")
    private String path;

    @Schema(description = "When the error was produced")
    private Instant timestamp;

    @Schema(description = "Correlation id for this request, matching the X-Request-Id response header")
    private String traceId;

    @Schema(description = "Per-field detail, present only on validation failures")
    private List<FieldError> fieldErrors;

    /** One rejected field. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "One rejected field")
    public static class FieldError {

        @Schema(description = "Dotted path to the field, e.g. template.components[0].text")
        private String field;

        @Schema(description = "Why this field was rejected")
        private String message;

        @Schema(description = "Stable rule identifier, present for Meta composition-rule violations")
        private String code;

        @Schema(description = "The value that was rejected. Omitted when it may contain sensitive data.")
        private Object rejectedValue;
    }
}
