package com.aigreentick.services.template.api.internal.v1;

import com.aigreentick.services.template.api.mapper.TemplateDetailResponseMapper;
import com.aigreentick.services.template.api.response.ResponseMessage;
import com.aigreentick.services.template.api.response.TemplateDetailResponseDto;
import com.aigreentick.services.template.application.dto.result.TemplateDetailResult;
import com.aigreentick.services.template.application.port.in.GetTemplateUseCase;
import com.aigreentick.services.template.common.constant.ApiHeaders;
import com.aigreentick.services.template.common.constant.ApiPaths;
import com.aigreentick.services.template.common.constant.InternalHeaders;
import com.aigreentick.services.template.common.constant.SecuritySchemes;
import com.aigreentick.services.template.common.constant.TemplateConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service template reads. Not part of the public API.
 *
 * <pre>
 * GET /internal/v1/templates/{templateId}
 * GET /internal/v1/templates/lookup?name=...&amp;language=...
 *
 * X-Internal-Api-Key: &lt;shared secret&gt;
 * X-Project-Id:       101
 * X-Waba-Id:          109876543210   (lookup only)
 * X-Internal-Caller:  messaging-service
 * X-Request-Id:       &lt;correlation id&gt;
 * </pre>
 *
 * <h2>Why this exists alongside the identical public endpoint</h2>
 *
 * The Messaging Service currently reads templates through
 * {@code GET /api/v1/templates/{templateId}}, which is unauthenticated and
 * shares a route, a rate limit and a CORS policy with the browser-facing
 * surface. That endpoint is a live contract and is <em>unchanged</em>; this
 * one is the authenticated equivalent it can move to when convenient.
 *
 * <p>The response body is deliberately identical, so migrating is a change of
 * base URL plus one header — not a parsing change. Once Messaging has moved,
 * the public by-id endpoint can be reconsidered on its own merits. Until
 * then, both answer.
 *
 * <h2>Why the shapes must stay identical</h2>
 *
 * Both delegate to the same {@link GetTemplateUseCase} and the same
 * {@link TemplateDetailResponseMapper}. That is not incidental — it is what
 * stops the two surfaces drifting into subtly different JSON, which is the
 * failure mode that makes a migration like this never actually happen.
 *
 * <h2>Visibility in Swagger</h2>
 *
 * Controlled by {@code springdoc.paths-to-match} in YAML: production
 * restricts it to {@code /api/**} so these are not listed. That is
 * documentation hygiene, not security — the gateway deny rule on
 * {@code /internal/**} and {@code InternalApiAuthFilter} are what keep
 * outsiders out. An endpoint missing from Swagger still answers.
 */
@Slf4j
@Validated
@RestController
@RequestMapping(ApiPaths.INTERNAL_TEMPLATES)
@RequiredArgsConstructor
@SecurityRequirement(name = SecuritySchemes.INTERNAL_API_KEY)
@Tag(name = "Internal Templates", description = "Service-to-service template reads")
public class InternalTemplateController {

    private final GetTemplateUseCase getTemplateUseCase;
    private final TemplateDetailResponseMapper templateDetailResponseMapper;

    @GetMapping(ApiPaths.TEMPLATE_BY_ID)
    @Operation(summary = "Get a template by id",
            description = "Authenticated equivalent of GET /api/v1/templates/{templateId}. "
                    + "Response body is byte-for-byte the same shape.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid internal API key"),
            @ApiResponse(responseCode = "404", description = "No such template in this project")
    })
    public ResponseEntity<ResponseMessage<TemplateDetailResponseDto>> getById(
            @Parameter(description = "Template identifier", example = "1024", required = true)
            @PathVariable @NotNull @Positive Long templateId,

            @Parameter(description = "Project the caller is acting for", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Calling service name, for audit trails")
            @RequestHeader(value = InternalHeaders.CALLER_SERVICE, required = false) String caller) {

        log.info("Internal template read templateId={} projectId={} caller={}", templateId, projectId, caller);

        TemplateDetailResult template = getTemplateUseCase.getById(templateId, projectId);
        return ResponseEntity.ok(ResponseMessage.success(
                TemplateConstants.Messages.TEMPLATE_FETCHED,
                templateDetailResponseMapper.mapToDetailResponse(template)));
    }

    /**
     * Lookup by natural key.
     *
     * <p>Worth having on the send path: a message references a template by
     * name and language, which is what the sender actually knows. Without
     * this the caller has to keep its own name-to-id mapping in step with
     * this service's, and that mapping goes stale silently.
     */
    @GetMapping(ApiPaths.TEMPLATE_LOOKUP)
    @Operation(summary = "Look up a template by name and language",
            description = "Resolves a template by its natural key within a WABA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid internal API key"),
            @ApiResponse(responseCode = "404", description = "No matching template")
    })
    public ResponseEntity<ResponseMessage<TemplateDetailResponseDto>> lookup(
            @Parameter(description = "Project the caller is acting for", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Meta WABA identifier", example = "109876543210", required = true)
            @RequestHeader(ApiHeaders.WABA_ID) @NotBlank String wabaId,

            @Parameter(description = "Template name", example = "welcome_offer", required = true)
            @RequestParam @NotBlank String name,

            @Parameter(description = "Template language code", example = "en_US", required = true)
            @RequestParam @NotBlank String language,

            @Parameter(description = "Calling service name, for audit trails")
            @RequestHeader(value = InternalHeaders.CALLER_SERVICE, required = false) String caller) {

        log.info("Internal template lookup name={} language={} projectId={} wabaId={} caller={}",
                name, language, projectId, wabaId, caller);

        TemplateDetailResult template = getTemplateUseCase.getByNameAndLanguage(projectId, name, language, wabaId);
        return ResponseEntity.ok(ResponseMessage.success(
                TemplateConstants.Messages.TEMPLATE_FETCHED,
                templateDetailResponseMapper.mapToDetailResponse(template)));
    }
}
