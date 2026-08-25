package com.aigreentick.services.template.common.constant;

/**
 * MDC keys populated by
 * {@link com.aigreentick.services.template.common.logging.CorrelationIdFilter}
 * and consumed by the logging pattern in {@code application.yaml}.
 *
 * <p>Defining them once means a rename cannot leave the filter and the YAML
 * disagreeing — a mismatch that does not fail the build and does not throw
 * at runtime, it just silently prints blank fields on every log line.
 */
public final class LogKeys {

    /** Correlation id for one request, propagated across services. */
    public static final String TRACE_ID = "traceId";

    /** Tenant the request is acting for, when known. */
    public static final String ORG_ID = "orgId";

    /** Project the request is acting for, when known. */
    public static final String PROJECT_ID = "projectId";

    private LogKeys() {
    }
}
