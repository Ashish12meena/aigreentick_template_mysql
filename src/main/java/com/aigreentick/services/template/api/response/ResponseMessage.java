package com.aigreentick.services.template.api.response;

import com.aigreentick.services.template.domain.enums.ResponseStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The success envelope wrapping every 2xx body this API returns.
 *
 * <h2>The shape is frozen</h2>
 *
 * {@code {"status": ..., "message": ..., "data": ...}} is what the Messaging
 * Service parses on the send path, so the three field names, their order of
 * appearance and the fact that {@code status} is the <em>string</em>
 * {@code "SUCCESS"} rather than a boolean or a number are all part of the
 * contract. Serialization is snake_case like the rest of the surface, though
 * none of these three names is affected by that.
 *
 * <p>The factory methods below exist because every controller method was
 * previously hand-rolling {@code new ResponseMessage<>(ResponseStatus.SUCCESS.name(), ...)}.
 * That is three chances per call site to pass the wrong literal, and one
 * controller had already drifted into reporting {@code "Template created
 * successfully"} from the submit-draft endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard success response envelope")
public class ResponseMessage<T> {

    @Schema(description = "SUCCESS or ERROR", example = "SUCCESS")
    private String status;

    @Schema(description = "Human-readable summary. Wording is not part of the contract.")
    private String message;

    @Schema(description = "The payload. Shape depends on the endpoint.")
    private T data;

    public static <T> ResponseMessage<T> success(String message, T data) {
        return new ResponseMessage<>(ResponseStatus.SUCCESS.name(), message, data);
    }

    /**
     * A 200 that nonetheless reports a partial failure.
     *
     * <p>Used where the local write succeeded but the Meta call did not — the
     * template exists and has an id, so answering 4xx/5xx would tell the
     * caller nothing was created, which is false and leads to duplicate
     * retries.
     */
    public static <T> ResponseMessage<T> partialFailure(String message, T data) {
        return new ResponseMessage<>(ResponseStatus.ERROR.name(), message, data);
    }
}
