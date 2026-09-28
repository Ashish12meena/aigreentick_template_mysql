package com.apargo.services.template.common.constant;

/**
 * JSON property names of the response wrapper ({@code ApiEnvelope}), for the
 * places that read or patch a serialized response instead of building one
 * (the idempotent replay). {@code ApiEnvelope}'s property order uses the same
 * constants, so the two cannot drift apart.
 */
public final class ResponseFields {

    private ResponseFields() {
    }

    public static final String SUCCESS = "success";
    public static final String STATUS = "status";
    public static final String CODE = "code";
    public static final String MESSAGE = "message";
    public static final String DATA = "data";
    public static final String ERRORS = "errors";
    public static final String META = "meta";

    /** Properties of {@code meta}. */
    public static final class Meta {

        private Meta() {
        }

        public static final String REQUEST_ID = "requestId";
        public static final String TRACE_ID = "traceId";
        public static final String TIMESTAMP = "timestamp";
        public static final String PATH = "path";
    }
}
