package com.aigreentick.services.template.common.constant;

/**
 * Header names on this service's public surface.
 *
 * <h2>Why tenancy travels in headers</h2>
 *
 * The path carries <em>resource identity</em> — the template being addressed.
 * {@code organizationId}, {@code projectId} and {@code wabaId} are
 * <em>caller context</em>: an assertion of "I am acting on behalf of this
 * tenant". Putting them in the path would mint several distinct URLs for one
 * template, which breaks caching and gateway routing for no gain.
 *
 * <p>They are deliberately not query parameters either: query strings are
 * written verbatim into access logs at every proxy hop.
 *
 * <p>These names are shared verbatim with waba-service
 * ({@code InternalHeaders}) and storage-service ({@code HeaderConstants}).
 * All three services must agree, so changing a value here is a
 * cross-service change.
 *
 * <h2>Trust level</h2>
 *
 * {@link #ORG_ID} and {@link #PROJECT_ID} are claims the caller makes about
 * itself, not proof. On the public surface the gateway is responsible for
 * populating them from an authenticated session; on the internal surface
 * they are only as trustworthy as {@link InternalHeaders#API_KEY}.
 */
public final class ApiHeaders {

    /** Tenant the caller is acting for. */
    public static final String ORG_ID = "X-Org-Id";

    /** Project scope within the tenant. */
    public static final String PROJECT_ID = "X-Project-Id";

    /** Meta's globally unique WhatsApp Business Account id. */
    public static final String WABA_ID = "X-Waba-Id";

    /** Correlation id propagated across services. */
    public static final String REQUEST_ID = "X-Request-Id";

    private ApiHeaders() {
    }
}
