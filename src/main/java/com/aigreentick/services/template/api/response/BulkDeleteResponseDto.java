package com.aigreentick.services.template.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Summary returned by the bulk delete ("Delete — returns a summary", 200).
 * Single deletes return 204 with no body.
 */
@Schema(description = "Result of a bulk delete")
public record BulkDeleteResponseDto(
        @Schema(description = "Number of templates soft-deleted", example = "12") int deletedCount) {
}
