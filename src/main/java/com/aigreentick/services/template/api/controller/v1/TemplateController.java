package com.aigreentick.services.template.api.controller.v1;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.aigreentick.services.template.api.constants.ApiHeaders;
import com.aigreentick.services.template.api.dto.request.create.CreateTemplateRequestDto;
import com.aigreentick.services.template.api.dto.response.*;
import com.aigreentick.services.template.api.dto.response.media.ResumableMediaUploadResponseDto;
import com.aigreentick.services.template.api.mapper.CreateTemplateApiMapper;
import com.aigreentick.services.template.api.mapper.TemplateDetailResponseMapper;
import com.aigreentick.services.template.api.mapper.TemplateResponseMapper;
import com.aigreentick.services.template.application.dto.result.TemplateResult;
import com.aigreentick.services.template.application.dto.result.TemplateSummaryResult;
import com.aigreentick.services.template.application.port.in.*;
import com.aigreentick.services.template.domain.enums.ResponseStatus;
import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for WhatsApp template management.
 *
 * Naming conventions:
 * POST /templates → create
 * GET /templates → list all (paginated)
 * GET /templates/{id} → get by ID
 * GET /templates/lookup → get by name + language
 * PUT /templates/{id}/draft → update draft
 * POST /templates/{id}/submit → submit draft to Meta
 * DELETE /templates/{id} → delete single
 * DELETE /templates → delete all by project
 * POST /templates/sync → sync from Facebook
 * POST /templates/media → upload media
 */
@RestController
@RequestMapping("api/v1/templates")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Template Management", description = "APIs for creating, retrieving, updating, submitting, syncing, and deleting WhatsApp message templates")
public class TemplateController {

        private final CreateTemplateUseCase createTemplate;
        private final GetTemplateUseCase getTemplate;
        private final UpdateDraftTemplateUseCase updateDraft;
        private final SubmitDraftToMetaUseCase submitDraft;
        private final DeleteTemplateUseCase deleteTemplate;
        private final SyncTemplateFromFacebookUseCase syncTemplates;
        private final WhatsappTemplateMediaUseCase mediaUpload;
        private final TemplateDetailResponseMapper detailMapper;
        private final CreateTemplateApiMapper createTemplateApiMapper;
        private final TemplateResponseMapper templateResponseMapper;

        // ── Create ──

        @PostMapping
        @Operation(summary = "Create a new WhatsApp template", description = "Creates a new WhatsApp message template on the WABA identified by X-Waba-Id. If marked as draft, it is saved locally without submission to Meta; otherwise it is submitted to Meta for approval.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Template created successfully (or created with a partial Meta submission error)"),
                        @ApiResponse(responseCode = "400", description = "Invalid request payload or missing tenancy headers"),
                        @ApiResponse(responseCode = "409", description = "A template with this name and language already exists on this WABA"),
                        @ApiResponse(responseCode = "502", description = "WABA credentials unavailable or Meta unreachable")
        })
        public ResponseEntity<ResponseMessage<TemplateResponseDto>> create(
                        @Parameter(description = "Project identifier", example = "101", required = true) @RequestHeader(ApiHeaders.PROJECT_ID) Long projectId,
                        @Parameter(description = "Organization identifier", example = "55", required = true) @RequestHeader(ApiHeaders.ORG_ID) Long organizationId,
                        @Parameter(description = "WhatsApp Business Account identifier", example = "109876543210", required = true) @RequestHeader(ApiHeaders.WABA_ID) @NotBlank(message = "X-Waba-Id must not be blank") String wabaId,
                        @Parameter(description = "Template creation payload", required = true) @RequestBody @Valid CreateTemplateRequestDto request) {

                log.info("POST /templates projectId={} orgId={} wabaId={} name={} isDraft={}",
                                projectId, organizationId, wabaId,
                                request.getTemplate() != null ? request.getTemplate().getName() : null,
                                request.isDraft());

                TemplateResult result = createTemplate.execute(
                                createTemplateApiMapper.toCommand(request, projectId, organizationId, wabaId));
                TemplateResponseDto response = createTemplateApiMapper.toResponseDto(result);
                return buildTemplateResponse(response);
        }

        // ── Read ──

        @GetMapping("/{id}")
        @Operation(summary = "Get template by ID", description = "Fetches full details of a single WhatsApp template by its unique identifier.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Template fetched successfully"),
                        @ApiResponse(responseCode = "404", description = "Template not found for the given project")
        })
        public ResponseEntity<ResponseMessage<TemplateDetailResponseDto>> getById(
                        @Parameter(description = "Template identifier", example = "1024", required = true) @PathVariable Long id,
                        @Parameter(description = "Project identifier", example = "101", required = true) @RequestHeader(ApiHeaders.PROJECT_ID) Long projectId) {

                WhatsappTemplate template = getTemplate.getById(id, projectId);
                return ok("Template fetched", detailMapper.mapToDetailResponse(template));
        }

        @GetMapping("/my-templates")
        @Operation(summary = "List templates", description = "Returns a paginated, filterable, and sortable list of WhatsApp templates for the given project.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Templates fetched successfully")
        })
        public ResponseEntity<ResponseMessage<Page<TemplateResponseDto>>> list(
                        @Parameter(description = "Project identifier", example = "101", required = true) @RequestHeader(ApiHeaders.PROJECT_ID) Long projectId,
                        @Parameter(description = "Project identifier", example = "101", required = true) @RequestHeader(ApiHeaders.ORG_ID) Long organizationId,
                        @Parameter(description = "Filter by template status", example = "APPROVED") @RequestParam(required = false) TemplateStatus status,
                        @Parameter(description = "Filter by template category", example = "MARKETING") @RequestParam(required = false) TemplateCategory category,
                        @Parameter(description = "Free-text search on template name", example = "welcome_offer") @RequestParam(required = false) String search,
                        @Parameter(description = "Zero-based page index", example = "0") @RequestParam(defaultValue = "0") int page,
                        @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10") int size,
                        @Parameter(description = "Field to sort by", example = "createdAt") @RequestParam(defaultValue = "createdAt") String sortBy,
                        @Parameter(description = "Sort direction", example = "desc") @RequestParam(defaultValue = "desc") String sortDir) {

                Page<TemplateSummaryResult> results = getTemplate.list(
                                projectId, status, category, search, page, size, sortBy, sortDir);
                Page<TemplateResponseDto> response = templateResponseMapper.toResponsePage(results);
                return ok("Templates fetched", response);
        }

        @GetMapping("/lookup")
        @Operation(summary = "Lookup template by name and language", description = "Fetches a single WhatsApp template identified by its name, language, and associated WABA account.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Template fetched successfully"),
                        @ApiResponse(responseCode = "404", description = "No matching template found")
        })
        public ResponseEntity<ResponseMessage<TemplateDetailResponseDto>> lookup(
                        @Parameter(description = "Project identifier", example = "101", required = true) @RequestHeader(ApiHeaders.PROJECT_ID) Long projectId,
                        @Parameter(description = "WhatsApp Business Account identifier", example = "109876543210", required = true) @RequestHeader(ApiHeaders.WABA_ID) String wabaId,
                        @Parameter(description = "Template name", example = "welcome_offer", required = true) @RequestParam String name,
                        @Parameter(description = "Template language code", example = "en_US", required = true) @RequestParam String language) {

                WhatsappTemplate template = getTemplate.getByNameAndLanguage(
                                projectId, name, language, wabaId);
                return ok("Template fetched", detailMapper.mapToDetailResponse(template));
        }

        // ── Update ──

        @PutMapping("/{id}/draft")
        @Operation(summary = "Update a draft template", description = "Updates the contents of an existing draft template prior to submission to Meta.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Draft updated successfully"),
                        @ApiResponse(responseCode = "400", description = "Invalid request payload or missing tenancy headers"),
                        @ApiResponse(responseCode = "404", description = "Template not found for the given project"),
                        @ApiResponse(responseCode = "409", description = "Another template with this name and language already exists on this WABA")
        })
        public ResponseEntity<ResponseMessage<TemplateResponseDto>> updateDraft(
                        @Parameter(description = "Template identifier", example = "1024", required = true) @PathVariable Long id,
                        @Parameter(description = "Project identifier", example = "101", required = true) @RequestHeader(ApiHeaders.PROJECT_ID) Long projectId,
                        @Parameter(description = "Organization identifier", example = "55", required = true) @RequestHeader(ApiHeaders.ORG_ID) Long organizationId,
                        @Parameter(description = "WhatsApp Business Account identifier", example = "109876543210", required = true) @RequestHeader(ApiHeaders.WABA_ID) @NotBlank(message = "X-Waba-Id must not be blank") String wabaId,
                        @Parameter(description = "Updated template payload", required = true) @RequestBody @Valid CreateTemplateRequestDto request) {

                TemplateResult updateResult = updateDraft.execute(
                                createTemplateApiMapper.toUpdateCommand(request, id, projectId, organizationId,
                                                wabaId));
                TemplateResponseDto response = createTemplateApiMapper.toResponseDto(updateResult);
                return ok("Draft updated", response);
        }

        @PostMapping("/{id}/submit")
        @Operation(summary = "Submit draft template to Meta", description = "Submits a previously created draft template to Meta for review and approval.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Template submitted successfully (or submitted with a partial Meta error)"),
                        @ApiResponse(responseCode = "404", description = "Draft template not found for the given project")
        })
        public ResponseEntity<ResponseMessage<TemplateResponseDto>> submitDraft(
                        @Parameter(description = "Template identifier", example = "1024", required = true) @PathVariable Long id,
                        @Parameter(description = "Project identifier", example = "101", required = true) @RequestHeader(ApiHeaders.PROJECT_ID) Long projectId) {

                TemplateResult submitResult = submitDraft.execute(id, projectId);
                TemplateResponseDto response = createTemplateApiMapper.toResponseDto(submitResult);
                return buildTemplateResponse(response);
        }

        // ── Delete ──

        @DeleteMapping("/{id}")
        @Operation(summary = "Delete a template", description = "Deletes a single WhatsApp template by ID, optionally deleting it from Meta as well.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Template deleted successfully"),
                        @ApiResponse(responseCode = "404", description = "Template not found for the given project")
        })
        public ResponseEntity<ResponseMessage<DeleteResponseDto>> delete(
                        @Parameter(description = "Template identifier", example = "1024", required = true) @PathVariable Long id,
                        @Parameter(description = "Project identifier", example = "101", required = true) @RequestHeader(ApiHeaders.PROJECT_ID) Long projectId,
                        @Parameter(description = "Whether to also delete the template from Meta", example = "false") @RequestParam(defaultValue = "false") boolean deleteFromMeta) {

                int deleted = deleteTemplate.deleteById(id, projectId, deleteFromMeta);
                DeleteResponseDto data = DeleteResponseDto.builder()
                                .deletedCount(deleted).projectId(projectId).templateId(id).build();
                return ok("Template deleted", data);
        }

        @DeleteMapping
        @Operation(summary = "Delete all templates for a project", description = "Deletes every WhatsApp template associated with the given project.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Templates deleted successfully")
        })
        public ResponseEntity<ResponseMessage<DeleteResponseDto>> deleteAll(
                        @Parameter(description = "Project identifier", example = "101", required = true) @RequestHeader(ApiHeaders.PROJECT_ID) Long projectId) {

                int deleted = deleteTemplate.deleteAllByProject(projectId);
                DeleteResponseDto data = DeleteResponseDto.builder()
                                .deletedCount(deleted).projectId(projectId).build();
                return ok("Deleted " + deleted + " templates", data);
        }

        // ── Sync ──

        @PostMapping("/sync")
        @Operation(summary = "Sync templates from Meta", description = "Triggers a background job that synchronizes templates from Facebook/Meta into the local project store. Returns immediately with 202 Accepted.")
        @ApiResponses({
                        @ApiResponse(responseCode = "202", description = "Sync job accepted and started in the background")
        })
        public ResponseEntity<ResponseMessage<TemplateSyncStats>> sync(
                        @Parameter(description = "Project identifier", example = "101", required = true) @RequestHeader(ApiHeaders.PROJECT_ID) Long projectId,
                        @Parameter(description = "Organization identifier", example = "55", required = true) @RequestHeader(ApiHeaders.ORG_ID) Long organizationId,
                        @Parameter(description = "WhatsApp Business Account identifier", example = "109876543210", required = true) @RequestHeader(ApiHeaders.WABA_ID) String wabaId) {

                log.info("POST /sync — scheduling background sync. projectId={} wabaId={}", projectId, wabaId);

                TemplateSyncStats stats = syncTemplates.execute(projectId, organizationId, wabaId);

                // 202 Accepted: request is valid, processing in background
                return ResponseEntity
                                .status(HttpStatus.ACCEPTED)
                                .body(new ResponseMessage<>(
                                                ResponseStatus.SUCCESS.name(),
                                                "Template sync started in background. Check logs for completion details.",
                                                stats));
        }

        // ── Media ──

        @PostMapping("/media")
        @Operation(summary = "Upload template media", description = "Uploads a media file (image, video, or document) to be used as a header attachment in a WhatsApp template, via resumable upload to Meta.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Media uploaded successfully"),
                        @ApiResponse(responseCode = "400", description = "Unsupported or invalid media file")
        })
        public ResponseEntity<ResponseMessage<ResumableMediaUploadResponseDto>> uploadMedia(
                        @Parameter(description = "Media file to upload", required = true) @RequestPart("file") MultipartFile file,
                        @Parameter(description = "Project identifier", example = "101", required = true) @RequestHeader(ApiHeaders.PROJECT_ID) Long projectId,
                        @Parameter(description = "Organization identifier", example = "55", required = true) @RequestHeader(ApiHeaders.ORG_ID) Long organizationId,
                        @Parameter(description = "WhatsApp Business Account identifier", example = "109876543210", required = true) @RequestHeader(ApiHeaders.WABA_ID) String wabaId) {

                ResumableMediaUploadResponseDto response = mediaUpload.uploadMedia(
                                file, projectId, organizationId, wabaId);
                return ok("Media uploaded", response);
        }

        @GetMapping("/health")
        @Operation(summary = "Health check", description = "Returns the current health status of the template service.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Service is running")
        })
        public ResponseEntity<ResponseMessage<Map<String, Object>>> health() {
                Map<String, Object> info = Map.of(
                                "service", "template-service",
                                "status", "UP",
                                "timestamp", Instant.now().toString());
                return ok("Service is running", info);
        }

        // ── Response helpers ──

        private <T> ResponseEntity<ResponseMessage<T>> ok(String message, T data) {
                return ResponseEntity.ok(
                                new ResponseMessage<>(ResponseStatus.SUCCESS.name(), message, data));
        }

        /**
         * Builds response for template operations that may have Meta errors
         * (errorMessage != null means partial failure — template saved but Meta
         * rejected).
         */
        private ResponseEntity<ResponseMessage<TemplateResponseDto>> buildTemplateResponse(
                        TemplateResponseDto response) {
                boolean hasError = response.getErrorMessage() != null;
                String status = hasError ? ResponseStatus.ERROR.name() : ResponseStatus.SUCCESS.name();
                String message = hasError ? response.getErrorMessage() : "Template created successfully";
                return ResponseEntity.ok(new ResponseMessage<>(status, message, response));
        }
}