package com.aigreentick.services.template.api.v1;

import com.aigreentick.services.template.api.mapper.CreateTemplateApiMapper;
import com.aigreentick.services.template.api.mapper.TemplateDetailResponseMapper;
import com.aigreentick.services.template.api.mapper.TemplateResponseMapper;
import com.aigreentick.services.template.api.request.CreateTemplateRequestDto;
import com.aigreentick.services.template.api.response.BulkDeleteResponseDto;
import com.aigreentick.services.template.api.response.SyncAcceptedResponseDto;
import com.aigreentick.services.template.api.response.TemplateDetailResponseDto;
import com.aigreentick.services.template.api.response.TemplateResponseDto;
import com.aigreentick.services.template.api.response.common.ApiEnvelope;
import com.aigreentick.services.template.api.response.common.PageResponse;
import com.aigreentick.services.template.api.response.common.Responses;
import com.aigreentick.services.template.api.response.media.ResumableMediaUploadResponseDto;
import com.aigreentick.services.template.api.validation.OneOf;
import com.aigreentick.services.template.application.dto.result.TemplateDetailResult;
import com.aigreentick.services.template.application.dto.result.TemplateResult;
import com.aigreentick.services.template.application.dto.result.TemplateSummaryResult;
import com.aigreentick.services.template.application.port.in.CreateTemplateUseCase;
import com.aigreentick.services.template.application.port.in.DeleteTemplateUseCase;
import com.aigreentick.services.template.application.port.in.GetTemplateUseCase;
import com.aigreentick.services.template.application.port.in.SubmitDraftToMetaUseCase;
import com.aigreentick.services.template.application.port.in.SyncTemplateFromFacebookUseCase;
import com.aigreentick.services.template.application.port.in.UpdateDraftTemplateUseCase;
import com.aigreentick.services.template.application.port.in.WhatsappTemplateMediaUseCase;
import com.aigreentick.services.template.common.constant.ApiHeaders;
import com.aigreentick.services.template.common.constant.ApiPaths;
import com.aigreentick.services.template.common.constant.LogKeys;
import com.aigreentick.services.template.common.constant.TemplateConstants;
import com.aigreentick.services.template.common.constant.TemplateConstants.SortFields;
import com.aigreentick.services.template.common.web.Idempotent;
import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

/**
 * Public REST surface for WhatsApp template management, following the
 * company API Standard: every JSON response uses the {@link ApiEnvelope}
 * wrapper, statuses follow the standard's operation table, lists are
 * {@code {items, pagination}}, and tenancy comes only from headers.
 *
 * <p>Contains no business logic: each method logs, calls one {@code port.in}
 * use case and wraps the result via {@link Responses}. Errors are thrown and
 * rendered by {@code GlobalExceptionHandler}.
 *
 * <h2>Documented exceptions to the wrapper</h2>
 * <ul>
 *   <li>{@code DELETE /{templateId}} answers {@code 204 No Content} with no body.</li>
 * </ul>
 *
 * <h2>Meta rejection is a successful call</h2>
 *
 * Create and submit persist locally first and then call Meta. If Meta
 * rejects the template, the local write still happened: the template exists,
 * has an id and now has status {@code FAILED}. The standard forbids
 * {@code 200} with {@code success: false}, and an error status would tell
 * the client nothing was created (prompting a retry that fails as a
 * duplicate). So these return 2xx with {@code data.errorMessage} /
 * {@code data.errorPayload} set and a message saying Meta did not accept it.
 *
 * <p>{@code GET /{templateId}} is read by the Messaging Service on the send
 * path; its {@code /internal} twin must stay identical.
 */
@Slf4j
@Validated
@RestController
@RequestMapping(ApiPaths.TEMPLATES)
@RequiredArgsConstructor
@Tag(name = "Templates",
        description = "Create, retrieve, update, submit, sync and delete WhatsApp message templates. "
                + "All JSON responses use the standard wrapper {success, status, code, message, data, errors, meta}.")
public class TemplateController {

    private final CreateTemplateUseCase createTemplateUseCase;
    private final GetTemplateUseCase getTemplateUseCase;
    private final UpdateDraftTemplateUseCase updateDraftTemplateUseCase;
    private final SubmitDraftToMetaUseCase submitDraftToMetaUseCase;
    private final DeleteTemplateUseCase deleteTemplateUseCase;
    private final SyncTemplateFromFacebookUseCase syncTemplateUseCase;
    private final WhatsappTemplateMediaUseCase templateMediaUseCase;

    private final CreateTemplateApiMapper createTemplateApiMapper;
    private final TemplateDetailResponseMapper templateDetailResponseMapper;
    private final TemplateResponseMapper templateResponseMapper;

    // ----------------------------------------------------
    // Create
    // ----------------------------------------------------

    @Idempotent
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a template",
            description = "Creates a template on the WABA identified by X-Waba-Id. A draft is stored "
                    + "locally without submission; otherwise it is submitted to Meta for approval. "
                    + "If Meta rejects the submission the template is still created (status FAILED) and "
                    + "data.errorMessage holds Meta's reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created. Location header points at the new template."),
            @ApiResponse(responseCode = "200", description = "Replay of an earlier request with the same X-Idempotency-Key"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid header, or unreadable body"),
            @ApiResponse(responseCode = "409", description = "TEMPLATE_ALREADY_EXISTS, or idempotency key conflict"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED: invalid fields or a Meta composition rule (META_* codes)"),
            @ApiResponse(responseCode = "502", description = "WABA_CREDENTIALS_UNAVAILABLE or DEPENDENCY_FAILURE"),
            @ApiResponse(responseCode = "504", description = "TIMEOUT: an upstream took too long")
    })
    public ResponseEntity<ApiEnvelope<TemplateResponseDto>> create(
            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Meta WABA identifier", example = "109876543210", required = true)
            @RequestHeader(ApiHeaders.WABA_ID) @NotBlank String wabaId,

            @RequestBody @Valid CreateTemplateRequestDto request) {

        log.info("Create template organizationId={} projectId={} wabaId={} draft={}",
                organizationId, projectId, wabaId, request.isDraft());

        TemplateResult result = createTemplateUseCase.execute(
                createTemplateApiMapper.toCommand(request, projectId, organizationId, wabaId));
        TemplateResponseDto response = createTemplateApiMapper.toResponseDto(result);

        String message = request.isDraft()
                ? TemplateConstants.Messages.DRAFT_SAVED
                : TemplateConstants.Messages.TEMPLATE_CREATED;

        return Responses.created(
                URI.create(ApiPaths.templateLocation(response.getId())),
                metaAwareMessage(response, message, TemplateConstants.Messages.CREATED_META_REJECTED),
                response);
    }

    // ----------------------------------------------------
    // Read
    // ----------------------------------------------------

    /** Read by the Messaging Service on the send path; keep identical to the /internal twin. */
    @GetMapping(ApiPaths.TEMPLATE_BY_ID)
    @Operation(summary = "Get a template by id",
            description = "Full detail for one template, scoped to the calling project.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "TEMPLATE_NOT_FOUND")
    })
    public ResponseEntity<ApiEnvelope<TemplateDetailResponseDto>> getById(
            @Parameter(description = "Template identifier", example = "1024", required = true)
            @PathVariable @NotNull @Positive Long templateId,

            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId) {

        log.info("Get template templateId={} organizationId={} projectId={}", templateId, organizationId, projectId);

        TemplateDetailResult template = getTemplateUseCase.getById(templateId, projectId);
        return Responses.ok(TemplateConstants.Messages.TEMPLATE_FETCHED,
                templateDetailResponseMapper.mapToDetailResponse(template));
    }

    @GetMapping(ApiPaths.TEMPLATE_LIST)
    @Operation(summary = "List templates",
            description = "Page-based list for the calling project. Sortable by createdAt, updatedAt, name, "
                    + "status, category, language; ties are broken by id so paging is stable. "
                    + "A page past the end returns 200 with items: [].")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of templates (data = {items, pagination})"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED: invalid page, size, sort, order or filter")
    })
    public ResponseEntity<ApiEnvelope<PageResponse<TemplateResponseDto>>> list(
            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Filter by status", example = "APPROVED")
            @RequestParam(required = false) TemplateStatus status,

            @Parameter(description = "Filter by category", example = "MARKETING")
            @RequestParam(required = false) TemplateCategory category,

            @Parameter(description = "Free-text search on template name", example = "welcome_offer")
            @RequestParam(required = false) String search,

            @Parameter(description = "Page number, starting at 0", example = "0")
            @RequestParam(defaultValue = TemplateConstants.Defaults.PAGE) @Min(0) int page,

            @Parameter(description = "Items per page, 1 to 100", example = "20")
            @RequestParam(defaultValue = TemplateConstants.Defaults.SIZE)
            @Min(1) @Max(TemplateConstants.Defaults.MAX_SIZE) int size,

            @Parameter(description = "Field to sort by", example = "createdAt")
            @RequestParam(defaultValue = TemplateConstants.Defaults.SORT)
            @OneOf({SortFields.CREATED_AT, SortFields.UPDATED_AT, SortFields.NAME,
                    SortFields.STATUS, SortFields.CATEGORY, SortFields.LANGUAGE}) String sort,

            @Parameter(description = "Sort direction: asc or desc", example = "desc")
            @RequestParam(defaultValue = TemplateConstants.Defaults.ORDER)
            @OneOf(value = {"asc", "desc"}, ignoreCase = true) String order) {

        log.info("List templates organizationId={} projectId={} status={} category={} page={} size={} sort={} order={}",
                organizationId, projectId, status, category, page, size, sort, order);

        Page<TemplateSummaryResult> results = getTemplateUseCase.list(
                projectId, status, category, search, page, size, sort, order);

        return Responses.ok(TemplateConstants.Messages.TEMPLATES_FETCHED,
                templateResponseMapper.toPageResponse(results));
    }

    @GetMapping(ApiPaths.TEMPLATE_LOOKUP)
    @Operation(summary = "Look up a template by name and language",
            description = "Resolves a template by its natural key within a WABA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "TEMPLATE_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED: name or language missing")
    })
    public ResponseEntity<ApiEnvelope<TemplateDetailResponseDto>> lookup(
            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Meta WABA identifier", example = "109876543210", required = true)
            @RequestHeader(ApiHeaders.WABA_ID) @NotBlank String wabaId,

            @Parameter(description = "Template name", example = "welcome_offer", required = true)
            @RequestParam @NotBlank String name,

            @Parameter(description = "Template language code", example = "en_US", required = true)
            @RequestParam @NotBlank String language) {

        log.info("Lookup template name={} language={} organizationId={} projectId={} wabaId={}",
                name, language, organizationId, projectId, wabaId);

        TemplateDetailResult template = getTemplateUseCase.getByNameAndLanguage(projectId, name, language, wabaId);
        return Responses.ok(TemplateConstants.Messages.TEMPLATE_FETCHED,
                templateDetailResponseMapper.mapToDetailResponse(template));
    }

    // ----------------------------------------------------
    // Update
    // ----------------------------------------------------

    @PutMapping(value = ApiPaths.TEMPLATE_DRAFT, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a draft template",
            description = "Replaces the contents of a template that has not yet been submitted to Meta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Draft updated; data is the updated template"),
            @ApiResponse(responseCode = "404", description = "TEMPLATE_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "TEMPLATE_INVALID_STATE (no longer a draft) or TEMPLATE_ALREADY_EXISTS"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    })
    public ResponseEntity<ApiEnvelope<TemplateResponseDto>> updateDraft(
            @Parameter(description = "Template identifier", example = "1024", required = true)
            @PathVariable @NotNull @Positive Long templateId,

            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Meta WABA identifier", example = "109876543210", required = true)
            @RequestHeader(ApiHeaders.WABA_ID) @NotBlank String wabaId,

            @RequestBody @Valid CreateTemplateRequestDto request) {

        log.info("Update draft templateId={} organizationId={} projectId={} wabaId={}",
                templateId, organizationId, projectId, wabaId);

        TemplateResult result = updateDraftTemplateUseCase.execute(
                createTemplateApiMapper.toUpdateCommand(request, templateId, projectId, organizationId, wabaId));

        return Responses.ok(TemplateConstants.Messages.DRAFT_UPDATED, createTemplateApiMapper.toResponseDto(result));
    }

    @Idempotent
    @PostMapping(ApiPaths.TEMPLATE_SUBMIT)
    @Operation(summary = "Submit a draft to Meta",
            description = "Sends a stored draft to Meta for review. If Meta rejects it, the call still "
                    + "returns 200: data.status is FAILED and data.errorMessage holds the reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Submitted (or rejected by Meta, see data.errorMessage)"),
            @ApiResponse(responseCode = "404", description = "TEMPLATE_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "TEMPLATE_INVALID_STATE, or idempotency key conflict"),
            @ApiResponse(responseCode = "502", description = "WABA_CREDENTIALS_UNAVAILABLE or DEPENDENCY_FAILURE"),
            @ApiResponse(responseCode = "504", description = "TIMEOUT")
    })
    public ResponseEntity<ApiEnvelope<TemplateResponseDto>> submitDraft(
            @Parameter(description = "Template identifier", example = "1024", required = true)
            @PathVariable @NotNull @Positive Long templateId,

            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId) {

        log.info("Submit draft templateId={} organizationId={} projectId={}", templateId, organizationId, projectId);

        TemplateResult result = submitDraftToMetaUseCase.execute(templateId, projectId);
        TemplateResponseDto response = createTemplateApiMapper.toResponseDto(result);

        return Responses.ok(
                metaAwareMessage(response, TemplateConstants.Messages.TEMPLATE_SUBMITTED,
                        TemplateConstants.Messages.SUBMIT_META_REJECTED),
                response);
    }

    // ----------------------------------------------------
    // Delete
    // ----------------------------------------------------

    @DeleteMapping(ApiPaths.TEMPLATE_BY_ID)
    @Operation(summary = "Delete a template",
            description = "Soft-deletes a template locally, and optionally deletes it from Meta. "
                    + "Note that Meta deletes every language variant sharing the template name. "
                    + "Returns 204 with no body.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "404", description = "TEMPLATE_NOT_FOUND")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Template identifier", example = "1024", required = true)
            @PathVariable @NotNull @Positive Long templateId,

            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Also delete the template from Meta", example = "false")
            @RequestParam(defaultValue = "false") boolean deleteFromMeta) {

        log.info("Delete template templateId={} organizationId={} projectId={} deleteFromMeta={}",
                templateId, organizationId, projectId, deleteFromMeta);

        deleteTemplateUseCase.deleteById(templateId, projectId, deleteFromMeta);
        return Responses.noContent();
    }

    /**
     * Bulk delete for the calling project. Takes no body and reads the
     * project from the header, so a caller can only ever name its own project.
     */
    @DeleteMapping
    @Operation(summary = "Delete every template in the project",
            description = "Soft-deletes all templates belonging to the calling project and returns how many.")
    @ApiResponse(responseCode = "200", description = "Deleted; data.deletedCount is the number removed")
    public ResponseEntity<ApiEnvelope<BulkDeleteResponseDto>> deleteAll(
            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId) {

        log.warn("Bulk delete of all templates requested organizationId={} projectId={}", organizationId, projectId);

        int deleted = deleteTemplateUseCase.deleteAllByProject(projectId);
        return Responses.ok(TemplateConstants.Messages.TEMPLATES_DELETED, new BulkDeleteResponseDto(deleted));
    }

    // ----------------------------------------------------
    // Sync
    // ----------------------------------------------------

    @PostMapping(ApiPaths.TEMPLATE_SYNC)
    @Operation(summary = "Sync templates from Meta",
            description = "Starts a background reconciliation of this WABA's templates from Meta. "
                    + "Returns 202 immediately with {jobId, statusUrl}; the template list shows the outcome.")
    @ApiResponse(responseCode = "202", description = "Sync accepted and running in the background")
    public ResponseEntity<ApiEnvelope<SyncAcceptedResponseDto>> sync(
            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Meta WABA identifier", example = "109876543210", required = true)
            @RequestHeader(ApiHeaders.WABA_ID) @NotBlank String wabaId) {

        log.info("Sync requested organizationId={} projectId={} wabaId={}", organizationId, projectId, wabaId);

        syncTemplateUseCase.execute(projectId, organizationId, wabaId);

        return Responses.accepted(TemplateConstants.Messages.SYNC_ACCEPTED, new SyncAcceptedResponseDto(
                MDC.get(LogKeys.REQUEST_ID),
                ApiPaths.TEMPLATES + ApiPaths.TEMPLATE_LIST));
    }

    // ----------------------------------------------------
    // Media
    // ----------------------------------------------------

    @PostMapping(value = ApiPaths.TEMPLATE_MEDIA, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload template header media",
            description = "Uploads an image, video or document to Meta via a resumable upload session "
                    + "and returns the handle to reference from a template header. "
                    + "X-Waba-Id selects the access token; X-App-Id is the Meta app the session is opened on.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uploaded; data holds the media handle"),
            @ApiResponse(responseCode = "413", description = "PAYLOAD_TOO_LARGE"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED: file missing or unsupported"),
            @ApiResponse(responseCode = "502",
                    description = "MEDIA_UPLOAD_FAILED, WABA_CREDENTIALS_UNAVAILABLE or DEPENDENCY_FAILURE")
    })
    public ResponseEntity<ApiEnvelope<ResumableMediaUploadResponseDto>> uploadMedia(
            @Parameter(description = "Media file", required = true)
            @RequestPart("file") MultipartFile file,

            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Meta WABA identifier", example = "109876543210", required = true)
            @RequestHeader(ApiHeaders.WABA_ID) @NotBlank String wabaId,

            @Parameter(description = "Meta app id the WABA's access token belongs to",
                    example = "1234567890123456", required = true)
            @RequestHeader(ApiHeaders.APP_ID) @NotBlank String appId) {

        log.info("Upload template media filename={} size={} organizationId={} projectId={} wabaId={} appId={}",
                file.getOriginalFilename(), file.getSize(), organizationId, projectId, wabaId, appId);

        ResumableMediaUploadResponseDto response =
                templateMediaUseCase.uploadMedia(file, projectId, organizationId, wabaId, appId);

        return Responses.ok(TemplateConstants.Messages.MEDIA_UPLOADED, response);
    }

    // ----------------------------------------------------
    // Helpers
    // ----------------------------------------------------

    /** Picks the response message only; the status code never depends on Meta's verdict. */
    private String metaAwareMessage(TemplateResponseDto response, String successMessage, String rejectedFormat) {
        if (response.getErrorMessage() == null) {
            return successMessage;
        }
        log.warn("Meta did not accept templateId={}: {}", response.getId(), response.getErrorMessage());
        return String.format(rejectedFormat, response.getErrorMessage());
    }
}
