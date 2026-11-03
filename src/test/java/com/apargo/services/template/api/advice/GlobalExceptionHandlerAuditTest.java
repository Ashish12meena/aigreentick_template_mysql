package com.apargo.services.template.api.advice;

import com.apargo.services.template.application.audit.TemplateAuditEvents;
import com.apargo.services.template.common.error.ErrorCode;
import com.apargo.services.template.common.exception.ExternalServiceException;
import com.apargo.services.template.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Which responses the handler audits as {@code API_REQUEST_FAILED} (doc 39). */
class GlobalExceptionHandlerAuditTest {

    private final TemplateAuditEvents audit = mock(TemplateAuditEvents.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(audit);

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/templates/3640");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/templates/{id}");
        return request;
    }

    @Test
    void anUnexpectedFailureIsAuditedWithItsRouteNotItsPath() {
        NullPointerException failure = new NullPointerException();

        assertThat(handler.handleUnexpected(failure, request()).getStatusCode().value()).isEqualTo(500);

        verify(audit).requestFailed(ErrorCode.INTERNAL_ERROR, "GET", "/api/v1/templates/{id}", 500, failure);
    }

    @Test
    void anApplication5xxIsAudited() {
        ExternalServiceException failure = new ExternalServiceException("Meta said no");

        assertThat(handler.handleApplicationException(failure, request()).getStatusCode().value()).isEqualTo(502);

        verify(audit).requestFailed(ErrorCode.DEPENDENCY_FAILURE, "GET", "/api/v1/templates/{id}", 502, failure);
    }

    @Test
    void aClientErrorIsNotAudited() {
        handler.handleApplicationException(
                new ResourceNotFoundException(ErrorCode.TEMPLATE_NOT_FOUND, "gone"), request());

        verify(audit, never()).requestFailed(any(), any(), any(), anyInt(), any());
    }

    @Test
    void withoutAMatchedRouteTheUriIsUsed() {
        NullPointerException failure = new NullPointerException();

        handler.handleUnexpected(failure, new MockHttpServletRequest("POST", "/api/v1/templates"));

        verify(audit).requestFailed(ErrorCode.INTERNAL_ERROR, "POST", "/api/v1/templates", 500, failure);
    }
}
