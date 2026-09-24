package com.aigreentick.services.template.api.response.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One entry of {@code errors[]} (API Standard §6).
 *
 * @param field   name as sent in the request, e.g. {@code template.components[0].text}
 *                or a query parameter such as {@code size}
 * @param code    a standard field code ({@code REQUIRED}, {@code OUT_OF_RANGE}, ...)
 *                or, for Meta composition rules, the rule's {@code META_*} code
 * @param message human-readable reason
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "One rejected field")
public record ApiFieldError(
        @Schema(example = "template.name") String field,
        @Schema(example = "REQUIRED") String code,
        @Schema(example = "name is required") String message) {
}
