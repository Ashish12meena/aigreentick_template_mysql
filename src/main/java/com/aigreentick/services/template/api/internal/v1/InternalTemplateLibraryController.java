package com.aigreentick.services.template.api.internal.v1;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.api.mapper.SystemTemplateApiMapper;
import com.aigreentick.services.template.api.request.SaveSystemTemplateRequestDto;
import com.aigreentick.services.template.api.response.SystemTemplateDetailResponseDto;
import com.aigreentick.services.template.api.response.common.ApiEnvelope;
import com.aigreentick.services.template.api.response.common.Responses;
import com.aigreentick.services.template.application.port.in.CreateSystemTemplateUseCase;
import com.aigreentick.services.template.application.port.in.UpdateSystemTemplateUseCase;
import com.aigreentick.services.template.common.constant.ApiHeaders;
import com.aigreentick.services.template.common.constant.ApiPaths;
import com.aigreentick.services.template.common.constant.InternalHeaders;
import com.aigreentick.services.template.common.constant.SecuritySchemes;
import com.aigreentick.services.template.common.constant.TemplateConstants;
import com.aigreentick.services.template.common.web.Idempotent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Template Library maintenance. Lives under {@code /internal} because the
 * public API trusts gateway headers and has no roles: anyone who could reach
 * a public write endpoint could change templates every organization sees.
 * {@code InternalApiAuthFilter} guards this path with the internal API key.
 *
 * <p>{@code X-Org-Id} / {@code X-Project-Id} identify the caller (and scope
 * the idempotency key); they do not own the library entry.
 *
 * <p>Entries are never deleted: send {@code active: false} to hide one.
 */
@Slf4j
@Validated
@RestController
@RequestMapping(ApiPaths.INTERNAL_TEMPLATE_LIBRARY)
@RequiredArgsConstructor
@SecurityRequirement(name = SecuritySchemes.INTERNAL_API_KEY)
@Tag(name = "Internal Template Library", description = "Create and update system library templates")
public class InternalTemplateLibraryController {

    private final CreateSystemTemplateUseCase createSystemTemplateUseCase;
    private final UpdateSystemTemplateUseCase updateSystemTemplateUseCase;
    private final SystemTemplateApiMapper systemTemplateApiMapper;

    @Idempotent
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a library template",
            description = "Validates the template with the same Meta rules as a user's template and adds it "
                    + "to the library. template and variables use the create-template request shape.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created. Location points at the public read path."),
            @ApiResponse(responseCode = "200", description = "Replay of an earlier request with the same X-Idempotency-Key"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: missing or invalid internal API key"),
            @ApiResponse(responseCode = "409", description = "SYSTEM_TEMPLATE_ALREADY_EXISTS, or idempotency key conflict"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED: invalid fields or a Meta rule (META_* codes)")
    })
    public ResponseEntity<ApiEnvelope<SystemTemplateDetailResponseDto>> create(
            @Parameter(description = "Organization the caller is acting for", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project the caller is acting for", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Calling service name, for audit trails")
            @RequestHeader(value = InternalHeaders.CALLER_SERVICE, required = false) String caller,

            @RequestBody @Valid SaveSystemTemplateRequestDto request) {

        log.info("Create library template name={} language={} organizationId={} projectId={} caller={}",
                request.getTemplate().getName(), request.getTemplate().getLanguage(),
                organizationId, projectId, caller);

        SystemTemplateDetailResponseDto response = systemTemplateApiMapper.toDetailResponse(
                createSystemTemplateUseCase.execute(systemTemplateApiMapper.toCommand(request)));

        return Responses.created(
                URI.create(ApiPaths.systemTemplateLocation(response.getId())),
                TemplateConstants.Messages.SYSTEM_TEMPLATE_CREATED,
                response);
    }

    @PutMapping(value = ApiPaths.SYSTEM_TEMPLATE_BY_ID, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace a library template",
            description = "Full replace of content, description and active flag. Works on inactive entries. "
                    + "Templates users already created from this entry do not change.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated; data is the new library template"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: missing or invalid internal API key"),
            @ApiResponse(responseCode = "404", description = "SYSTEM_TEMPLATE_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "SYSTEM_TEMPLATE_ALREADY_EXISTS"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    })
    public ResponseEntity<ApiEnvelope<SystemTemplateDetailResponseDto>> update(
            @Parameter(description = "Library template identifier", example = "12", required = true)
            @PathVariable @NotNull @Positive Long systemTemplateId,

            @Parameter(description = "Organization the caller is acting for", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project the caller is acting for", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Calling service name, for audit trails")
            @RequestHeader(value = InternalHeaders.CALLER_SERVICE, required = false) String caller,

            @RequestBody @Valid SaveSystemTemplateRequestDto request) {

        log.info("Update library template systemTemplateId={} active={} organizationId={} projectId={} caller={}",
                systemTemplateId, request.isActive(), organizationId, projectId, caller);

        return Responses.ok(TemplateConstants.Messages.SYSTEM_TEMPLATE_UPDATED,
                systemTemplateApiMapper.toDetailResponse(
                        updateSystemTemplateUseCase.execute(systemTemplateId, systemTemplateApiMapper.toCommand(request))));
    }
}
