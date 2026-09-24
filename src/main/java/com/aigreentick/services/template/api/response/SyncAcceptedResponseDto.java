package com.aigreentick.services.template.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code data} of a 202 Accepted (API Standard §4).
 *
 * @param jobId     identifier of the background sync. It is the request id,
 *                  which the sync thread keeps in its logs (MDC is propagated
 *                  to the sync pool), so one id finds the whole job.
 * @param statusUrl where to observe the outcome. There is no job-status
 *                  resource yet, so this is the template list itself, which
 *                  the standard allows ("or the resource itself").
 */
@Schema(description = "Background job accepted")
public record SyncAcceptedResponseDto(
        @Schema(example = "3f2a9c0d8e1b4a7f9c2d6e5b1a0f4c3d") String jobId,
        @Schema(example = "/api/v1/templates/my-templates") String statusUrl) {
}
