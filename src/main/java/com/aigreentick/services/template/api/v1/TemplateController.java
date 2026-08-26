package com.aigreentick.services.template.api.v1;

import com.aigreentick.services.template.api.mapper.CreateTemplateApiMapper;
import com.aigreentick.services.template.api.mapper.TemplateDetailResponseMapper;
import com.aigreentick.services.template.api.mapper.TemplateResponseMapper;
import com.aigreentick.services.template.api.request.CreateTemplateRequestDto;
import com.aigreentick.services.template.api.response.DeleteResponseDto;
import com.aigreentick.services.template.api.response.ResponseMessage;
import com.aigreentick.services.template.api.response.TemplateDetailResponseDto;
import com.aigreentick.services.template.api.response.TemplateResponseDto;
import com.aigreentick.services.template.api.response.TemplateSyncStats;
import com.aigreentick.services.template.api.response.media.ResumableMediaUploadResponseDto;
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
import com.aigreentick.services.template.common.constant.TemplateConstants;
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
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
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

/**
 * Public REST surface for WhatsApp template management.
 *
 * <p>Contains no business logic — every method delegates immediately to a
 * {@code port.in} use case, per {@code docs/rules.md}.
 *
 * <h2>Frozen endpoint</h2>
 *
 * {@code GET /api/v1/templates/{templateId}} is consumed by the Messaging
 * Service on the message-send path. Its path, its {@code X-Project-Id}
 * requirement and its {@code {status, message, data}} snake_case envelope are
 * a live contract and are unchanged here. Everything else on this controller
 * was free to be tidied.
 *
 * <h2>What changed</h2>
 *
 * <ul>
 *   <li>Routes come from {@link ApiPaths} rather than string literals. The
 *       class-level mapping was {@code "api/v1/templates"} — no leading
 *       slash, which Spring happens to tolerate.</li>
 *   <li>The {@code GET /health} endpoint was removed. It hand-rolled a status
 *       object that always reported {@code UP} because the only way to reach
 *       it was for the service to already be up; it never checked the
 *       database or any upstream. Spring Boot Actuator's
 *       {@code /actuator/health} does the real thing and is what the
 *       Kubernetes probes in {@code application.yaml} point at.</li>
 *   <li>Tenancy headers are validated ({@code @Positive}, {@code @NotBlank})
 *       instead of being trusted. A negative or zero {@code X-Project-Id}
 *       previously reached the query layer and returned an empty page, which
 *       reads as "you have no templates" rather than "that header is
 *       wrong".</li>
 *   <li>Page size is bounded. {@code size} was unbounded, so
 *       {@code ?size=1000000} was an unauthenticated way to ask the database
 *       for everything.</li>
 *   <li>{@code @RequestHeader} for {@code X-Org-Id} was declared on the list
 *       endpoint and then never used; it is removed rather than left as a
 *       required header nothing reads.</li>
 * </ul>
 */
@Slf4j
@Validated
@RestController
@RequestMapping(ApiPaths.TEMPLATES)
@RequiredArgsConstructor
@Tag(name = "Templates",
        description = "Create, retrieve, update, submit, sync and delete WhatsApp message templates. "
                + "All request and response bodies use snake_case field names.")
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

    @PostMapping
    @Operation(summary = "Create a template",
            description = "Creates a template on the WABA identified by X-Waba-Id. A draft is stored "
                    + "locally without submission; otherwise it is submitted to Meta for approval.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Created. If Meta rejected the submission the template still exists "
                            + "locally and status is ERROR with the reason in message."),
            @ApiResponse(responseCode = "400", description = "Invalid payload or tenancy headers"),
            @ApiResponse(responseCode = "409", description = "Name and language already exist on this WABA"),
            @ApiResponse(responseCode = "422", description = "Payload breaks a Meta composition rule"),
            @ApiResponse(responseCode = "502", description = "WABA credentials unavailable or Meta unreachable")
    })
    public ResponseEntity<ResponseMessage<TemplateResponseDto>> create(
            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Meta WABA identifier", example = "109876543210", required = true)
            @RequestHeader(ApiHeaders.WABA_ID) @NotBlank String wabaId,

            @RequestBody @Valid CreateTemplateRequestDto request) {

        log.info("Create template projectId={} organizationId={} wabaId={} draft={}",
                projectId, organizationId, wabaId, request.isDraft());

        TemplateResult result = createTemplateUseCase.execute(
                createTemplateApiMapper.toCommand(request, projectId, organizationId, wabaId));

        return metaAwareResponse(createTemplateApiMapper.toResponseDto(result),
                TemplateConstants.Messages.TEMPLATE_CREATED);
    }

    // ----------------------------------------------------
    // Read
    // ----------------------------------------------------

    /**
     * FROZEN — consumed by the Messaging Service on the send path. See the
     * class Javadoc before changing anything about this method's path,
     * headers or response shape.
     */
    @GetMapping(ApiPaths.TEMPLATE_BY_ID)
    @Operation(summary = "Get a template by id",
            description = "Full detail for one template, scoped to the calling project.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "No such template in this project")
    })
    public ResponseEntity<ResponseMessage<TemplateDetailResponseDto>> getById(
            @Parameter(description = "Template identifier", example = "1024", required = true)
            @PathVariable @NotNull @Positive Long templateId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId) {

        log.info("Get template templateId={} projectId={}", templateId, projectId);

        TemplateDetailResult template = getTemplateUseCase.getById(templateId, projectId);
        return ok(TemplateConstants.Messages.TEMPLATE_FETCHED,
                templateDetailResponseMapper.mapToDetailResponse(template));
    }

    @GetMapping(ApiPaths.TEMPLATE_LIST)
    @Operation(summary = "List templates",
            description = "Paginated, filterable and sortable listing for the calling project.")
    @ApiResponse(responseCode = "200", description = "Page of templates")
    public ResponseEntity<ResponseMessage<Page<TemplateResponseDto>>> list(
            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Filter by status", example = "APPROVED")
            @RequestParam(required = false) TemplateStatus status,

            @Parameter(description = "Filter by category", example = "MARKETING")
            @RequestParam(required = false) TemplateCategory category,

            @Parameter(description = "Free-text search on template name", example = "welcome_offer")
            @RequestParam(required = false) String search,

            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(TemplateConstants.Defaults.MAX_SIZE) int size,

            @Parameter(description = "Field to sort by", example = "createdAt")
            @RequestParam(defaultValue = TemplateConstants.Defaults.SORT_BY) String sortBy,

            @Parameter(description = "Sort direction", example = "desc")
            @RequestParam(defaultValue = TemplateConstants.Defaults.SORT_DIRECTION) String sortDir) {

        log.info("List templates projectId={} status={} category={} page={} size={}",
                projectId, status, category, page, size);

        Page<TemplateSummaryResult> results = getTemplateUseCase.list(
                projectId, status, category, search, page, size, sortBy, sortDir);

        return ok(TemplateConstants.Messages.TEMPLATES_FETCHED,
                templateResponseMapper.toResponsePage(results));
    }

    @GetMapping(ApiPaths.TEMPLATE_LOOKUP)
    @Operation(summary = "Look up a template by name and language",
            description = "Resolves a template by its natural key within a WABA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "No matching template")
    })
    public ResponseEntity<ResponseMessage<TemplateDetailResponseDto>> lookup(
            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Meta WABA identifier", example = "109876543210", required = true)
            @RequestHeader(ApiHeaders.WABA_ID) @NotBlank String wabaId,

            @Parameter(description = "Template name", example = "welcome_offer", required = true)
            @RequestParam @NotBlank String name,

            @Parameter(description = "Template language code", example = "en_US", required = true)
            @RequestParam @NotBlank String language) {

        log.info("Lookup template name={} language={} projectId={} wabaId={}",
                name, language, projectId, wabaId);

        TemplateDetailResult template = getTemplateUseCase.getByNameAndLanguage(projectId, name, language, wabaId);
        return ok(TemplateConstants.Messages.TEMPLATE_FETCHED,
                templateDetailResponseMapper.mapToDetailResponse(template));
    }

    // ----------------------------------------------------
    // Update
    // ----------------------------------------------------

    @PutMapping(ApiPaths.TEMPLATE_DRAFT)
    @Operation(summary = "Update a draft template",
            description = "Replaces the contents of a template that has not yet been submitted to Meta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Draft updated"),
            @ApiResponse(responseCode = "404", description = "No such template in this project"),
            @ApiResponse(responseCode = "409", description = "Name and language already exist on this WABA"),
            @ApiResponse(responseCode = "422", description = "Template is no longer a draft, or breaks a Meta rule")
    })
    public ResponseEntity<ResponseMessage<TemplateResponseDto>> updateDraft(
            @Parameter(description = "Template identifier", example = "1024", required = true)
            @PathVariable @NotNull @Positive Long templateId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Meta WABA identifier", example = "109876543210", required = true)
            @RequestHeader(ApiHeaders.WABA_ID) @NotBlank String wabaId,

            @RequestBody @Valid CreateTemplateRequestDto request) {

        log.info("Update draft templateId={} projectId={} wabaId={}", templateId, projectId, wabaId);

        TemplateResult result = updateDraftTemplateUseCase.execute(
                createTemplateApiMapper.toUpdateCommand(request, templateId, projectId, organizationId, wabaId));

        return ok(TemplateConstants.Messages.DRAFT_UPDATED, createTemplateApiMapper.toResponseDto(result));
    }

    @PostMapping(ApiPaths.TEMPLATE_SUBMIT)
    @Operation(summary = "Submit a draft to Meta",
            description = "Sends a stored draft to Meta for review.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Submitted. If Meta rejected it, status is ERROR with the reason in message."),
            @ApiResponse(responseCode = "404", description = "No such draft in this project"),
            @ApiResponse(responseCode = "502", description = "WABA credentials unavailable or Meta unreachable")
    })
    public ResponseEntity<ResponseMessage<TemplateResponseDto>> submitDraft(
            @Parameter(description = "Template identifier", example = "1024", required = true)
            @PathVariable @NotNull @Positive Long templateId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId) {

        log.info("Submit draft templateId={} projectId={}", templateId, projectId);

        TemplateResult result = submitDraftToMetaUseCase.execute(templateId, projectId);

        // Previously reported "Template created successfully" here - copied
        // from the create endpoint, and wrong: nothing is created by a submit.
        return metaAwareResponse(createTemplateApiMapper.toResponseDto(result),
                TemplateConstants.Messages.TEMPLATE_SUBMITTED);
    }

    // ----------------------------------------------------
    // Delete
    // ----------------------------------------------------

    @DeleteMapping(ApiPaths.TEMPLATE_BY_ID)
    @Operation(summary = "Delete a template",
            description = "Soft-deletes a template locally, and optionally deletes it from Meta. "
                    + "Note that Meta deletes every language variant sharing the template name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deleted"),
            @ApiResponse(responseCode = "404", description = "No such template in this project")
    })
    public ResponseEntity<ResponseMessage<DeleteResponseDto>> delete(
            @Parameter(description = "Template identifier", example = "1024", required = true)
            @PathVariable @NotNull @Positive Long templateId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Also delete the template from Meta", example = "false")
            @RequestParam(defaultValue = "false") boolean deleteFromMeta) {

        log.info("Delete template templateId={} projectId={} deleteFromMeta={}",
                templateId, projectId, deleteFromMeta);

        int deleted = deleteTemplateUseCase.deleteById(templateId, projectId, deleteFromMeta);

        return ok(TemplateConstants.Messages.TEMPLATE_DELETED, DeleteResponseDto.builder()
                .deletedCount(deleted)
                .projectId(projectId)
                .templateId(templateId)
                .build());
    }

    /**
     * Bulk delete for the calling project.
     *
     * <p>Deliberately keeps no request body and takes the project from the
     * header, so there is no way to name a project other than the one the
     * caller is already scoped to.
     */
    @DeleteMapping
    @Operation(summary = "Delete every template in the project",
            description = "Soft-deletes all templates belonging to the calling project.")
    @ApiResponse(responseCode = "200", description = "Deleted")
    public ResponseEntity<ResponseMessage<DeleteResponseDto>> deleteAll(
            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId) {

        log.warn("Bulk delete of all templates requested for projectId={}", projectId);

        int deleted = deleteTemplateUseCase.deleteAllByProject(projectId);

        return ok("Deleted " + deleted + " template(s)", DeleteResponseDto.builder()
                .deletedCount(deleted)
                .projectId(projectId)
                .build());
    }

    // ----------------------------------------------------
    // Sync
    // ----------------------------------------------------

    @PostMapping(ApiPaths.TEMPLATE_SYNC)
    @Operation(summary = "Sync templates from Meta",
            description = "Starts a background reconciliation of this WABA's templates from Meta. "
                    + "Returns 202 immediately; poll the template list for the outcome.")
    @ApiResponse(responseCode = "202", description = "Sync accepted and running in the background")
    public ResponseEntity<ResponseMessage<TemplateSyncStats>> sync(
            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Meta WABA identifier", example = "109876543210", required = true)
            @RequestHeader(ApiHeaders.WABA_ID) @NotBlank String wabaId) {

        log.info("Sync requested projectId={} organizationId={} wabaId={}",
                projectId, organizationId, wabaId);

        TemplateSyncStats stats = syncTemplateUseCase.execute(projectId, organizationId, wabaId);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ResponseMessage.success(TemplateConstants.Messages.SYNC_ACCEPTED, stats));
    }

    // ----------------------------------------------------
    // Media
    // ----------------------------------------------------

    @PostMapping(value = ApiPaths.TEMPLATE_MEDIA, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload template header media",
            description = "Uploads an image, video or document to Meta via a resumable upload session "
                    + "and returns the handle to reference from a template header.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Uploaded"),
            @ApiResponse(responseCode = "400", description = "Unsupported or invalid media file"),
            @ApiResponse(responseCode = "413", description = "File exceeds the configured limit"),
            @ApiResponse(responseCode = "502", description = "WABA credentials unavailable or Meta unreachable")
    })
    public ResponseEntity<ResponseMessage<ResumableMediaUploadResponseDto>> uploadMedia(
            @Parameter(description = "Media file", required = true)
            @RequestPart("file") MultipartFile file,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Meta WABA identifier", example = "109876543210", required = true)
            @RequestHeader(ApiHeaders.WABA_ID) @NotBlank String wabaId) {

        log.info("Upload template media filename={} size={} projectId={} wabaId={}",
                file.getOriginalFilename(), file.getSize(), projectId, wabaId);

        ResumableMediaUploadResponseDto response =
                templateMediaUseCase.uploadMedia(file, projectId, organizationId, wabaId);

        return ok(TemplateConstants.Messages.MEDIA_UPLOADED, response);
    }

    // ----------------------------------------------------
    // Response helpers
    // ----------------------------------------------------

    private <T> ResponseEntity<ResponseMessage<T>> ok(String message, T data) {
        return ResponseEntity.ok(ResponseMessage.success(message, data));
    }

    /**
     * Create and submit both persist locally first and then call Meta, so
     * they have a third outcome beyond success and failure: the template
     * exists and has an id, but Meta rejected it.
     *
     * <p>That is reported as {@code 200} with {@code status: "ERROR"} rather
     * than as a 4xx/5xx, because answering with an error status would tell
     * the caller nothing was created — which is false, and leads to a retry
     * that then fails as a duplicate.
     */
    private ResponseEntity<ResponseMessage<TemplateResponseDto>> metaAwareResponse(
            TemplateResponseDto response, String successMessage) {

        if (response.getErrorMessage() != null) {
            log.warn("Meta rejected the operation for templateId={}: {}",
                    response.getId(), response.getErrorMessage());
            return ResponseEntity.ok(
                    ResponseMessage.partialFailure(response.getErrorMessage(), response));
        }
        return ResponseEntity.ok(ResponseMessage.success(successMessage, response));
    }
}
