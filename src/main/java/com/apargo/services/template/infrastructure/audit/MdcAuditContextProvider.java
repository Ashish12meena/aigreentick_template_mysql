package com.apargo.services.template.infrastructure.audit;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.apargo.services.template.application.dto.AuditContext;
import com.apargo.services.template.application.port.out.AuditContextProvider;
import com.apargo.services.template.common.constant.AuditConstants;
import com.apargo.services.template.common.constant.LogKeys;
import com.apargo.services.template.common.logging.TraceParent;
import com.apargo.services.template.infrastructure.config.properties.AuditProperties;
import com.apargo.platform.contract.audit.AuditActorDto;
import com.apargo.platform.contract.audit.AuditChannel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the audit context from the MDC, which the inbound filters
 * ({@code RequestIdFilter}, {@code TraceContextFilter}), {@code MdcTaskDecorator}
 * and {@code ScheduledJobContext} already populate on every request, pool
 * thread and job run. Reading the MDC rather than the servlet request is what
 * makes background work report the user who started it.
 *
 * <p>Actor and channel (Audit Producer Guide §3):
 * <table>
 *   <tr><th>Situation</th><th>actor</th><th>channel</th></tr>
 *   <tr><td>Scheduled job</td><td>SYSTEM / job name</td><td>WORKER</td></tr>
 *   <tr><td>Request with {@code X-User-Id}</td><td>USER / user id</td><td>WEB</td></tr>
 *   <tr><td>Internal call without a user</td><td>SERVICE / {@code X-Internal-Caller}</td><td>API</td></tr>
 *   <tr><td>Background work started by a request</td><td>as above</td><td>WORKER</td></tr>
 * </table>
 * A call with neither a user nor a known caller is recorded as
 * {@code SERVICE / unknown}: the event is still published, and the gap is
 * visible rather than attributed to a guess.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MdcAuditContextProvider implements AuditContextProvider {

    private final AuditProperties properties;

    @Override
    public AuditContext current() {
        boolean worker = LogKeys.WORKER_FLAG.equals(MDC.get(LogKeys.WORKER));
        String jobName = MDC.get(LogKeys.JOB_NAME);
        String userId = MDC.get(LogKeys.USER_ID);
        String caller = MDC.get(LogKeys.CALLER_SERVICE);

        AuditActorDto actor;
        AuditChannel channel;
        if (hasText(jobName)) {
            actor = AuditActorDto.system(jobName);
            channel = AuditChannel.WORKER;
        } else if (hasText(userId)) {
            actor = AuditActorDto.user(userId);
            channel = worker ? AuditChannel.WORKER : AuditChannel.WEB;
        } else {
            actor = AuditActorDto.service(hasText(caller) ? caller : AuditConstants.UNKNOWN_CALLER);
            channel = worker ? AuditChannel.WORKER : AuditChannel.API;
        }

        String traceId = MDC.get(LogKeys.TRACE_ID);
        if (!TraceParent.isValidTraceId(traceId)) {
            // Every entry point sets one; reaching here means a new entry
            // point skipped TraceContextFilter / ScheduledJobContext.
            traceId = TraceParent.newTraceId();
            log.warn("No trace id in context for an audit event; generated traceId={}", traceId);
        }

        return new AuditContext(
                actor,
                channel,
                jobName != null ? null : MDC.get(LogKeys.REQUEST_ID),
                traceId,
                null,
                null,
                properties.getSourceService(),
                properties.getEnvironment());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
