package com.aigreentick.services.template.api;

import com.aigreentick.services.template.api.internal.v1.InternalTemplateController;
import com.aigreentick.services.template.api.mapper.CreateTemplateApiMapper;
import com.aigreentick.services.template.api.mapper.TemplateDetailResponseMapper;
import com.aigreentick.services.template.api.mapper.TemplateResponseMapper;
import com.aigreentick.services.template.api.response.TemplateSyncStats;
import com.aigreentick.services.template.api.v1.TemplateController;
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
import com.aigreentick.services.template.application.validation.Violation;
import com.aigreentick.services.template.common.error.ErrorCode;
import com.aigreentick.services.template.common.exception.ExternalServiceException;
import com.aigreentick.services.template.common.exception.InvalidTemplateStateException;
import com.aigreentick.services.template.common.exception.ResourceNotFoundException;
import com.aigreentick.services.template.common.exception.TemplateRuleViolationException;
import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.aigreentick.services.template.infrastructure.config.properties.IdempotencyProperties;
import com.aigreentick.services.template.infrastructure.config.properties.InternalApiProperties;
import com.aigreentick.services.template.infrastructure.idempotency.IdempotencyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the HTTP contract to the company API Standard: wrapper shape, status
 * codes, error codes, pagination and headers. Business logic is mocked; these
 * tests only cover what the API layer promises.
 */
@WebMvcTest(controllers = {TemplateController.class, InternalTemplateController.class})
@Import({CreateTemplateApiMapper.class, TemplateDetailResponseMapper.class, TemplateResponseMapper.class})
@EnableConfigurationProperties({InternalApiProperties.class, IdempotencyProperties.class})
@TestPropertySource(properties = {
        "internal.api.path-prefix=/internal",
        "internal.api.api-key=test-internal-key-0123456789abcdef",
        "internal.api.auth-enabled=true",
        "idempotency.required=true"
})
class ApiStandardContractTest {

    private static final String ORG = "55";
    private static final String PROJECT = "101";
    private static final String WABA = "109876543210";

    private static final String VALID_BODY = """
            {"template": {"name": "welcome_offer", "language": "en_US", "category": "MARKETING",
              "components": [{"type": "BODY", "text": "Hello"}]}, "draft": true}
            """;

    @Autowired MockMvc mvc;

    @MockitoBean CreateTemplateUseCase createTemplateUseCase;
    @MockitoBean GetTemplateUseCase getTemplateUseCase;
    @MockitoBean UpdateDraftTemplateUseCase updateDraftTemplateUseCase;
    @MockitoBean SubmitDraftToMetaUseCase submitDraftToMetaUseCase;
    @MockitoBean DeleteTemplateUseCase deleteTemplateUseCase;
    @MockitoBean SyncTemplateFromFacebookUseCase syncTemplateUseCase;
    @MockitoBean WhatsappTemplateMediaUseCase templateMediaUseCase;
    @MockitoBean IdempotencyStore idempotencyStore;

    @BeforeEach
    void reserveIdempotencyKeys() {
        when(idempotencyStore.reserve(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(new IdempotencyStore.Decision(IdempotencyStore.Decision.Type.PROCEED, 1L, null, null));
    }

    private static TemplateResult templateResult(String errorMessage) {
        return TemplateResult.builder()
                .id(1024L).name("welcome_offer").language("en_US")
                .category(TemplateCategory.MARKETING)
                .status(errorMessage == null ? TemplateStatus.DRAFT : TemplateStatus.FAILED)
                .createdAt(Instant.parse("2026-01-15T10:30:00Z"))
                .updatedAt(Instant.parse("2026-01-15T10:30:00Z"))
                .errorMessage(errorMessage)
                .build();
    }

    // ------------------------------------------------------------------
    @Nested
    class SuccessWrapper {

        @Test
        void readReturnsFullWrapperAndEchoesRequestId() throws Exception {
            when(getTemplateUseCase.getById(1024L, 101L))
                    .thenReturn(TemplateDetailResult.builder().id(1024L).name("welcome_offer").build());

            mvc.perform(get("/api/v1/templates/1024")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT)
                            .header("X-Request-Id", "req-123"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Request-Id", "req-123"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").isString())
                    .andExpect(jsonPath("$.data.id").value(1024))
                    .andExpect(jsonPath("$.errors", hasSize(0)))
                    .andExpect(jsonPath("$.meta.requestId").value("req-123"))
                    .andExpect(jsonPath("$.meta.timestamp").isString())
                    .andExpect(jsonPath("$.meta.path").doesNotExist());
        }

        @Test
        void requestIdIsGeneratedWhenAbsentOrMalformed() throws Exception {
            when(getTemplateUseCase.getById(1024L, 101L))
                    .thenReturn(TemplateDetailResult.builder().id(1024L).build());

            mvc.perform(get("/api/v1/templates/1024")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT)
                            .header("X-Request-Id", "bad value\nwith newline"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Request-Id"))
                    .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not(containsString(" "))));
        }

        @Test
        void listUsesStandardDefaultsAndItemsPaginationShape() throws Exception {
            TemplateSummaryResult row = TemplateSummaryResult.builder().id(1L).name("a").build();
            when(getTemplateUseCase.list(eq(101L), isNull(), isNull(), isNull(), eq(0), eq(20), eq("createdAt"), eq("desc")))
                    .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 42));

            mvc.perform(get("/api/v1/templates/my-templates")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.pagination.page").value(0))
                    .andExpect(jsonPath("$.data.pagination.size").value(20))
                    .andExpect(jsonPath("$.data.pagination.totalItems").value(42))
                    .andExpect(jsonPath("$.data.pagination.totalPages").value(3))
                    .andExpect(jsonPath("$.data.pagination.hasNext").value(true))
                    .andExpect(jsonPath("$.data.pagination.hasPrevious").value(false))
                    .andExpect(jsonPath("$.data.content").doesNotExist());
        }

        @Test
        void pagePastTheEndIsEmptyItemsNotError() throws Exception {
            when(getTemplateUseCase.list(anyLong(), any(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(9, 20), 42));

            mvc.perform(get("/api/v1/templates/my-templates?page=9")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items", hasSize(0)))
                    .andExpect(jsonPath("$.data.pagination.hasNext").value(false));
        }

        @Test
        void createReturns201WithLocation() throws Exception {
            when(createTemplateUseCase.execute(any())).thenReturn(templateResult(null));

            mvc.perform(post("/api/v1/templates")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT).header("X-Waba-Id", WABA)
                            .header("X-Idempotency-Key", "k-1")
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/templates/1024"))
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.createdAt").value("2026-01-15T10:30:00Z"));

            verify(idempotencyStore).complete(eq(1L), eq(201), anyString());
        }

        @Test
        void metaRejectionIsStillASuccessfulCreate() throws Exception {
            when(createTemplateUseCase.execute(any())).thenReturn(templateResult("Invalid parameter"));

            mvc.perform(post("/api/v1/templates")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT).header("X-Waba-Id", WABA)
                            .header("X-Idempotency-Key", "k-2")
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message", containsString("Meta did not accept")))
                    .andExpect(jsonPath("$.data.status").value("FAILED"))
                    .andExpect(jsonPath("$.data.errorMessage").value("Invalid parameter"));
        }

        @Test
        void deleteIs204WithoutBody() throws Exception {
            mvc.perform(delete("/api/v1/templates/1024")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isNoContent())
                    .andExpect(header().exists("X-Request-Id"))
                    .andExpect(content().string(""));
        }

        @Test
        void bulkDeleteReturnsSummary() throws Exception {
            when(deleteTemplateUseCase.deleteAllByProject(101L)).thenReturn(7);

            mvc.perform(delete("/api/v1/templates")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.deletedCount").value(7));
        }

        @Test
        void syncIs202WithJobIdAndStatusUrl() throws Exception {
            when(syncTemplateUseCase.execute(101L, 55L, WABA)).thenReturn(TemplateSyncStats.started());

            mvc.perform(post("/api/v1/templates/sync")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT).header("X-Waba-Id", WABA)
                            .header("X-Request-Id", "job-1"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value(202))
                    .andExpect(jsonPath("$.data.jobId").value("job-1"))
                    .andExpect(jsonPath("$.data.statusUrl").value("/api/v1/templates/my-templates"));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    class ErrorWrapper {

        @Test
        void notFoundUsesResourceCodeAndNullData() throws Exception {
            when(getTemplateUseCase.getById(9L, 101L)).thenThrow(
                    new ResourceNotFoundException(ErrorCode.TEMPLATE_NOT_FOUND, "Template", "id", 9L));

            mvc.perform(get("/api/v1/templates/9")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"))
                    .andExpect(jsonPath("$.data").value(nullValue()))
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasKey("data")))
                    .andExpect(jsonPath("$.errors", hasSize(0)))
                    .andExpect(jsonPath("$.meta.path").value("/api/v1/templates/9"));
        }

        @Test
        void missingOrgHeaderIs400() throws Exception {
            mvc.perform(get("/api/v1/templates/1").header("X-Project-Id", PROJECT))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message", containsString("X-Org-Id")));
        }

        @Test
        void invalidTenancyHeaderIs400() throws Exception {
            mvc.perform(get("/api/v1/templates/1").header("X-Org-Id", ORG).header("X-Project-Id", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

            mvc.perform(get("/api/v1/templates/1").header("X-Org-Id", ORG).header("X-Project-Id", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }

        @Test
        void sizeOutOfRangeIs422() throws Exception {
            mvc.perform(get("/api/v1/templates/my-templates?size=500")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("size"))
                    .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));
        }

        @Test
        void unknownSortOrOrderIs422() throws Exception {
            mvc.perform(get("/api/v1/templates/my-templates?sort=password")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].field").value("sort"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));

            mvc.perform(get("/api/v1/templates/my-templates?order=sideways")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].field").value("order"));
        }

        @Test
        void unknownEnumFilterIs422() throws Exception {
            mvc.perform(get("/api/v1/templates/my-templates?status=NOPE")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].field").value("status"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
        }

        @Test
        void bodyValidationIs422WithFieldPathsAndStandardCodes() throws Exception {
            mvc.perform(post("/api/v1/templates")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT).header("X-Waba-Id", WABA)
                            .header("X-Idempotency-Key", "k-3")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"template": {"language": "en_US", "category": "MARKETING",
                                      "components": [{"type": "BODY"}]}}
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("template.name"))
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));

            verify(idempotencyStore).release(1L);
        }

        @Test
        void unknownEnumInBodyIs422OnThatField() throws Exception {
            mvc.perform(post("/api/v1/templates")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT).header("X-Waba-Id", WABA)
                            .header("X-Idempotency-Key", "k-4")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY.replace("MARKETING", "SPAM")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].field").value("template.category"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
        }

        @Test
        void malformedJsonIs400() throws Exception {
            mvc.perform(post("/api/v1/templates")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT).header("X-Waba-Id", WABA)
                            .header("X-Idempotency-Key", "k-5")
                            .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }

        @Test
        void metaRuleViolationIs422KeepingMetaCodes() throws Exception {
            when(createTemplateUseCase.execute(any())).thenThrow(new TemplateRuleViolationException(List.of(
                    Violation.of("template.components[0].text", "META_BODY_TOO_LONG", "Body exceeds 1024 characters"))));

            mvc.perform(post("/api/v1/templates")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT).header("X-Waba-Id", WABA)
                            .header("X-Idempotency-Key", "k-6")
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("template.components[0].text"))
                    .andExpect(jsonPath("$.errors[0].code").value("META_BODY_TOO_LONG"));
        }

        @Test
        void stateClashIs409() throws Exception {
            when(submitDraftToMetaUseCase.execute(1024L, 101L))
                    .thenThrow(new InvalidTemplateStateException("Template id=1024 is not in DRAFT status"));

            mvc.perform(post("/api/v1/templates/1024/submit")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT)
                            .header("X-Idempotency-Key", "k-7"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TEMPLATE_INVALID_STATE"));
        }

        @Test
        void upstreamTimeoutIs504() throws Exception {
            when(submitDraftToMetaUseCase.execute(1024L, 101L))
                    .thenThrow(new ExternalServiceException("Meta call failed", new TimeoutException("read timed out")));

            mvc.perform(post("/api/v1/templates/1024/submit")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT)
                            .header("X-Idempotency-Key", "k-8"))
                    .andExpect(status().isGatewayTimeout())
                    .andExpect(jsonPath("$.status").value(504))
                    .andExpect(jsonPath("$.code").value("TIMEOUT"));
        }

        @Test
        void unexpectedErrorHidesDetails() throws Exception {
            when(getTemplateUseCase.getById(1L, 101L)).thenThrow(new IllegalStateException("SELECT * FROM secret"));

            mvc.perform(get("/api/v1/templates/1").header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(containsString("SELECT"))));
        }

        @Test
        void unknownPathIs404InWrapper() throws Exception {
            mvc.perform(get("/api/v1/nothing-here").header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        void internalSurfaceWithoutKeyIs401InWrapper() throws Exception {
            mvc.perform(get("/internal/v1/templates/1").header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.data").value(nullValue()));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    class Idempotency {

        @Test
        void keyIsRequiredOnCreate() throws Exception {
            mvc.perform(post("/api/v1/templates")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT).header("X-Waba-Id", WABA)
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("X-Idempotency-Key")));

            verify(createTemplateUseCase, never()).execute(any());
        }

        @Test
        void completedKeyReplaysAs200WithoutRunningAgain() throws Exception {
            String stored = """
                    {"success":true,"status":201,"code":"SUCCESS","message":"Template created successfully",
                     "data":{"id":1024},"errors":[],"meta":{"requestId":"first","timestamp":"2026-01-15T10:30:00Z"}}
                    """;
            when(idempotencyStore.reserve(anyLong(), anyLong(), eq("k-9"), anyString()))
                    .thenReturn(new IdempotencyStore.Decision(IdempotencyStore.Decision.Type.REPLAY, 1L, 201, stored));

            mvc.perform(post("/api/v1/templates")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT).header("X-Waba-Id", WABA)
                            .header("X-Idempotency-Key", "k-9").header("X-Request-Id", "second")
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.id").value(1024))
                    .andExpect(jsonPath("$.meta.requestId").value("second"));

            verify(createTemplateUseCase, never()).execute(any());
        }

        @Test
        void keyReusedForDifferentRequestIs409() throws Exception {
            when(idempotencyStore.reserve(anyLong(), anyLong(), eq("k-10"), anyString()))
                    .thenReturn(new IdempotencyStore.Decision(IdempotencyStore.Decision.Type.REUSED, null, null, null));

            mvc.perform(post("/api/v1/templates")
                            .header("X-Org-Id", ORG).header("X-Project-Id", PROJECT).header("X-Waba-Id", WABA)
                            .header("X-Idempotency-Key", "k-10")
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        }

        @Test
        void nonIdempotentEndpointsIgnoreTheStore() throws Exception {
            when(deleteTemplateUseCase.deleteById(anyLong(), anyLong(), anyBoolean())).thenReturn(1);

            mvc.perform(delete("/api/v1/templates/1").header("X-Org-Id", ORG).header("X-Project-Id", PROJECT))
                    .andExpect(status().isNoContent());

            verify(idempotencyStore, never()).reserve(anyLong(), anyLong(), anyString(), anyString());
        }
    }
}
