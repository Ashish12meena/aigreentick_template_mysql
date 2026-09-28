package com.apargo.services.template.common.constant;

/**
 * MDC keys populated by
 * {@link com.apargo.services.template.common.logging.RequestIdFilter}
 * and consumed by the logging pattern in {@code application.yaml}.
 *
 * <p>The MDC is also the carrier for request context beyond logging: the
 * response wrapper reads {@link #REQUEST_ID} for {@code meta.requestId}, and
 * the outbound client filter forwards all four values to sibling services
 * (API Standard §1). Defining the keys once means a rename cannot leave the
 * producers and consumers disagreeing — a mismatch that fails silently.
 */
public final class LogKeys {

    /** Value of {@code X-Request-Id} for this request (received or generated). */
    public static final String REQUEST_ID = "requestId";

    /** Value of {@code X-Org-Id}, when sent. */
    public static final String ORG_ID = "orgId";

    /** Value of {@code X-Project-Id}, when sent. */
    public static final String PROJECT_ID = "projectId";

    /** Value of {@code X-User-Id}, when sent. */
    public static final String USER_ID = "userId";

    /**
     * 32-hex W3C trace id of this request or job, set by
     * {@code TraceContextFilter} or {@code ScheduledJobContext}. Feeds logs,
     * {@code meta.traceId}, outbound {@code traceparent} and audit events.
     * The Producer Guide's {@code traceId} log / MDC key.
     */
    public static final String TRACE_ID = "traceId";

    /** Value of {@code X-Internal-Caller} on {@code /internal/**} requests only. */
    public static final String CALLER_SERVICE = "callerService";

    /** Name of the scheduled job this thread is running, set by {@code ScheduledJobContext}. */
    public static final String JOB_NAME = "jobName";

    /**
     * {@code "true"} on pool threads running background work handed off by
     * a request ({@code MdcTaskDecorator}). Absent on request threads.
     */
    public static final String WORKER = "worker";

    /** Value of {@link #WORKER} on worker threads. */
    public static final String WORKER_FLAG = "true";

    private LogKeys() {
    }
}
