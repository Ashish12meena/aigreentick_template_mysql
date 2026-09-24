package com.aigreentick.services.template.common.constant;

/**
 * MDC keys populated by
 * {@link com.aigreentick.services.template.common.logging.RequestIdFilter}
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

    private LogKeys() {
    }
}
