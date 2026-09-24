package com.aigreentick.services.template.common.constant;

/**
 * Header names on this service's public surface (API Standard §1).
 *
 * <h2>Why tenancy travels in headers</h2>
 *
 * The path carries <em>resource identity</em> — the template being addressed.
 * {@code organizationId}, {@code projectId} and {@code wabaId} are
 * <em>caller context</em>. The standard requires organization and project to
 * come only from headers, never from the body or query string; query strings
 * are also written verbatim into access logs at every proxy hop.
 *
 * <p>These names are shared verbatim with waba-service and storage-service.
 * Changing a value here is a cross-service change.
 *
 * <h2>Trust level</h2>
 *
 * {@link #ORG_ID}, {@link #PROJECT_ID} and {@link #USER_ID} are claims, not
 * proof. On the public surface the gateway validates {@code Authorization}
 * and populates them; on the internal surface they are only as trustworthy
 * as {@link InternalHeaders#API_KEY}.
 */
public final class ApiHeaders {

    /** Organization the request acts for. Required on every business endpoint. */
    public static final String ORG_ID = "X-Org-Id";

    /** Project the request acts for. Required on every project-level endpoint. */
    public static final String PROJECT_ID = "X-Project-Id";

    /** User performing the action, when a user is acting. Optional; logged and forwarded. */
    public static final String USER_ID = "X-User-Id";

    /**
     * The only request-tracking header. Generated when absent, echoed on every
     * response and forwarded to sibling services. {@code X-Correlation-Id},
     * {@code X-Trace-Id} and similar names must not be used.
     */
    public static final String REQUEST_ID = "X-Request-Id";

    /** Stops a repeated create/send request from running twice. */
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    /** Meta's globally unique WhatsApp Business Account id (service-specific). */
    public static final String WABA_ID = "X-Waba-Id";

    /**
     * Meta app id. Required only by the media upload endpoint: Meta's
     * Resumable Upload API opens a session on {@code /{app-id}/uploads}, and
     * the app must be the one the WABA's access token was issued for. A
     * wrong value is rejected by Meta; it grants nothing.
     */
    public static final String APP_ID = "X-App-Id";

    private ApiHeaders() {
    }
}
