package com.aigreentick.services.template.common.constant;

/**
 * Headers used on the internal service-to-service surface
 * ({@code /internal/**}), and on outbound calls this service makes to other
 * internal services.
 *
 * <p>Tenancy headers are not repeated here — they are the same names on both
 * surfaces and live in {@link ApiHeaders}. Only the credentials and routing
 * metadata that exist <em>solely</em> between services belong in this class.
 *
 * <h2>Outbound use</h2>
 *
 * {@link #API_KEY} is sent by this service when calling waba-service and
 * storage-service. Before this existed, the WABA credential adapter sent no
 * key at all, which worked only because waba-service ships with
 * {@code internal.api.auth-enabled: false} in its dev profile. The first
 * environment to turn that on would have failed every template create,
 * submit, delete and sync with a 401 — and the failure would have surfaced
 * as "credentials not found", not as "we forgot the key".
 */
public final class InternalHeaders {

    /** Shared secret proving the caller is inside the trust boundary. */
    public static final String API_KEY = "X-Internal-Api-Key";

    /** Calling service name - for audit trails and per-caller rate limits. */
    public static final String CALLER_SERVICE = "X-Internal-Caller";

    /** Value this service sends as {@link #CALLER_SERVICE}. */
    public static final String THIS_SERVICE = "template-service";

    private InternalHeaders() {
    }
}
