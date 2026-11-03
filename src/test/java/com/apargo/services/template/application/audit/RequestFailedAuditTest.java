package com.apargo.services.template.application.audit;

import com.apargo.platform.contract.audit.AuditActorDto;
import com.apargo.platform.contract.audit.AuditChannel;
import com.apargo.platform.contract.audit.AuditErrorCategory;
import com.apargo.platform.contract.audit.AuditEventDto;
import com.apargo.platform.contract.audit.AuditEventStatus;
import com.apargo.platform.contract.audit.AuditEventValidator;
import com.apargo.services.template.application.dto.AuditContext;
import com.apargo.services.template.application.port.out.AuditContextProvider;
import com.apargo.services.template.common.constant.AuditConstants;
import com.apargo.services.template.common.error.ErrorCode;
import com.apargo.services.template.common.exception.ExternalServiceException;
import com.apargo.services.template.domain.enums.TemplateAuditEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What the audit trail says about a request answered with a 5xx (doc 39). */
class RequestFailedAuditTest {

    private static final String ROUTE = "/api/v1/templates/{id}";

    private final AuditContextProvider contextProvider = mock(AuditContextProvider.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final TemplateAuditEvents audit = new TemplateAuditEvents(contextProvider, events);

    @BeforeEach
    void context() {
        when(contextProvider.current()).thenReturn(new AuditContext(
                AuditActorDto.user("529"), AuditChannel.WEB, "request-1",
                "0123456789abcdef0123456789abcdef", null, null, "template-service", "development"));
        when(contextProvider.organizationId()).thenReturn(5L);
        when(contextProvider.projectId()).thenReturn(438L);
    }

    @Test
    void anUnexpectedExceptionNamesItsClassAndTheLineInOurCode() {
        audit.requestFailed(ErrorCode.INTERNAL_ERROR, "GET", ROUTE, 500, thrownHere());

        AuditEventDto event = raisedEvent();
        assertThat(event.eventType()).isEqualTo(TemplateAuditEventType.API_REQUEST_FAILED.name());
        assertThat(event.module()).isEqualTo(AuditConstants.MODULE);
        assertThat(event.status()).isEqualTo(AuditEventStatus.FAILURE);
        assertThat(event.orgId()).isEqualTo(5L);
        assertThat(event.projectId()).isEqualTo(438L);
        assertThat(event.metadata())
                .containsEntry(AuditConstants.Metadata.HTTP_METHOD, "GET")
                .containsEntry(AuditConstants.Metadata.ROUTE, ROUTE)
                .containsEntry(AuditConstants.Metadata.HTTP_STATUS, 500);
        assertThat(event.error().category()).isEqualTo(AuditErrorCategory.SYSTEM);
        assertThat(event.error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(event.error().reference()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(event.error().details())
                .containsEntry(AuditConstants.ErrorDetails.EXCEPTION, NullPointerException.class.getName())
                .doesNotContainKey(AuditConstants.ErrorDetails.ROOT_CAUSE);
        assertThat((String) event.error().details().get(AuditConstants.ErrorDetails.ORIGIN))
                .startsWith("RequestFailedAuditTest.thrownHere(RequestFailedAuditTest.java:");
        assertThat(AuditEventValidator.validate(event)).isEmpty();
    }

    @Test
    void theExceptionMessageNeverReachesTheTrail() {
        audit.requestFailed(ErrorCode.INTERNAL_ERROR, "POST", ROUTE, 500,
                new IllegalStateException("https://graph.facebook.com/v23.0/x?access_token=EAAG-secret"));

        assertThat(raisedEvent().toString()).doesNotContain("EAAG-secret");
    }

    @Test
    void anUpstreamFailureNamesItsRootCause() {
        ExternalServiceException wrapped = new ExternalServiceException("Meta said no",
                new ConnectException("Connection refused"));

        audit.requestFailed(ErrorCode.DEPENDENCY_FAILURE, "POST", ROUTE, 502, wrapped);

        AuditEventDto event = raisedEvent();
        assertThat(event.error().category()).isEqualTo(AuditErrorCategory.EXTERNAL_SERVICE);
        assertThat(event.error().details())
                .containsEntry(AuditConstants.ErrorDetails.EXCEPTION, ExternalServiceException.class.getName())
                .containsEntry(AuditConstants.ErrorDetails.ROOT_CAUSE, ConnectException.class.getName());
        assertThat(event.error().message()).doesNotContain("Meta said no");
        assertThat(AuditEventValidator.validate(event)).isEmpty();
    }

    @Test
    void aRequestWithNoTenantIsAPlatformEvent() {
        when(contextProvider.organizationId()).thenReturn(null);
        when(contextProvider.projectId()).thenReturn(null);

        audit.requestFailed(ErrorCode.INTERNAL_ERROR, "GET", "/internal/v1/x", 500, new RuntimeException());

        AuditEventDto event = raisedEvent();
        assertThat(event.orgId()).isEqualTo(AuditEventDto.PLATFORM_ORG_ID);
        assertThat(event.projectId()).isNull();
        assertThat(AuditEventValidator.validate(event)).isEmpty();
    }

    @Test
    void noCauseStillRaisesAValidEvent() {
        audit.requestFailed(ErrorCode.INTERNAL_ERROR, "GET", ROUTE, 500, null);

        assertThat(AuditEventValidator.validate(raisedEvent())).isEmpty();
    }

    private static NullPointerException thrownHere() {
        try {
            Object nothing = null;
            nothing.hashCode();
            throw new AssertionError("unreachable");
        } catch (NullPointerException e) {
            return e;
        }
    }

    private AuditEventDto raisedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        return (AuditEventDto) captor.getValue();
    }
}
