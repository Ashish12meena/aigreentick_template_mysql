package com.aigreentick.services.template.api.v1;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.api.mapper.SystemTemplateApiMapper;
import com.aigreentick.services.template.api.response.SystemTemplateDetailResponseDto;
import com.aigreentick.services.template.api.response.SystemTemplateSummaryResponseDto;
import com.aigreentick.services.template.api.response.common.ApiEnvelope;
import com.aigreentick.services.template.api.response.common.PageResponse;
import com.aigreentick.services.template.api.response.common.Responses;
import com.aigreentick.services.template.api.validation.OneOf;
import com.aigreentick.services.template.application.dto.result.SystemTemplateSummaryResult;
import com.aigreentick.services.template.application.port.in.GetSystemTemplateUseCase;
import com.aigreentick.services.template.common.constant.ApiHeaders;
import com.aigreentick.services.template.common.constant.ApiPaths;
import com.aigreentick.services.template.common.constant.TemplateConstants;
import com.aigreentick.services.template.common.constant.TemplateConstants.SortFields;
import com.aigreentick.services.template.domain.enums.TemplateCategory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Public, read-only Template Library: predefined templates any user can start
 * from.
 *
 * <h2>Tenancy</h2>
 * {@code X-Org-Id} / {@code X-Project-Id} are required like on every business
 * endpoint (and logged), but they do not filter the data: library templates
 * belong to the system, not to a project.
 *
 * <h2>Using a template</h2>
 * There is no "use" endpoint. {@code GET /{systemTemplateId}} returns
 * {@code template} and {@code variables} in the create-request shape; the
 * client pre-fills its form and posts to {@code POST /api/v1/templates}, so
 * validation, duplicate checks, drafts and Meta submission stay in one place.
 */
@Slf4j
@Validated
@RestController
@RequestMapping(ApiPaths.TEMPLATE_LIBRARY)
@RequiredArgsConstructor
@Tag(name = "Template Library",
        description = "Browse predefined system templates. Use one by sending its template and variables "
                + "to POST /api/v1/templates.")
public class TemplateLibraryController {

    private final GetSystemTemplateUseCase getSystemTemplateUseCase;
    private final SystemTemplateApiMapper systemTemplateApiMapper;

    @GetMapping
    @Operation(summary = "List library templates",
            description = "Page-based list of active library templates, filterable by category and language. "
                    + "search matches name and description. Ties are broken by id so paging is stable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of library templates (data = {items, pagination})"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED: invalid page, size, sort, order or filter")
    })
    public ResponseEntity<ApiEnvelope<PageResponse<SystemTemplateSummaryResponseDto>>> list(
            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId,

            @Parameter(description = "Filter by category", example = "UTILITY")
            @RequestParam(required = false) TemplateCategory category,

            @Parameter(description = "Filter by template language code", example = "en")
            @RequestParam(required = false) @Size(max = 10) String language,

            @Parameter(description = "Free-text search on name and description", example = "order")
            @RequestParam(required = false) String search,

            @Parameter(description = "Page number, starting at 0", example = "0")
            @RequestParam(defaultValue = TemplateConstants.Defaults.PAGE) @Min(0) int page,

            @Parameter(description = "Items per page, 1 to 100", example = "20")
            @RequestParam(defaultValue = TemplateConstants.Defaults.SIZE)
            @Min(1) @Max(TemplateConstants.Defaults.MAX_SIZE) int size,

            @Parameter(description = "Field to sort by", example = "createdAt")
            @RequestParam(defaultValue = TemplateConstants.Defaults.SORT)
            @OneOf({SortFields.CREATED_AT, SortFields.UPDATED_AT, SortFields.NAME,
                    SortFields.CATEGORY, SortFields.LANGUAGE}) String sort,

            @Parameter(description = "Sort direction: asc or desc", example = "desc")
            @RequestParam(defaultValue = TemplateConstants.Defaults.ORDER)
            @OneOf(value = {"asc", "desc"}, ignoreCase = true) String order) {

        log.info("List library templates organizationId={} projectId={} category={} language={} page={} size={} sort={} order={}",
                organizationId, projectId, category, language, page, size, sort, order);

        Page<SystemTemplateSummaryResult> results = getSystemTemplateUseCase.list(
                category, language, search, page, size, sort, order);

        return Responses.ok(TemplateConstants.Messages.SYSTEM_TEMPLATES_FETCHED,
                systemTemplateApiMapper.toPageResponse(results));
    }

    @GetMapping(ApiPaths.SYSTEM_TEMPLATE_BY_ID)
    @Operation(summary = "Get a library template",
            description = "Full library template. data.template and data.variables have the same shape as "
                    + "the create-template request body.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "SYSTEM_TEMPLATE_NOT_FOUND (missing or inactive)")
    })
    public ResponseEntity<ApiEnvelope<SystemTemplateDetailResponseDto>> getById(
            @Parameter(description = "Library template identifier", example = "12", required = true)
            @PathVariable @NotNull @Positive Long systemTemplateId,

            @Parameter(description = "Organization identifier", example = "55", required = true)
            @RequestHeader(ApiHeaders.ORG_ID) @NotNull @Positive Long organizationId,

            @Parameter(description = "Project identifier", example = "101", required = true)
            @RequestHeader(ApiHeaders.PROJECT_ID) @NotNull @Positive Long projectId) {

        log.info("Get library template systemTemplateId={} organizationId={} projectId={}",
                systemTemplateId, organizationId, projectId);

        return Responses.ok(TemplateConstants.Messages.SYSTEM_TEMPLATE_FETCHED,
                systemTemplateApiMapper.toDetailResponse(getSystemTemplateUseCase.getById(systemTemplateId)));
    }
}
