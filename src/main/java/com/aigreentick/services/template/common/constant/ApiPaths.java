package com.aigreentick.services.template.common.constant;

/**
 * Every HTTP path this service exposes, in one place.
 *
 * <p>This class previously existed but was an empty stub, so every route
 * lived as a string literal on {@code TemplateController} — including
 * {@code "api/v1/templates"}, which is missing its leading slash and worked
 * only because Spring tolerates that. Separately,
 * {@code TemplateConstants.Paths} declared a second, contradictory set of
 * path constants that no controller ever read. Two half-maintained copies of
 * the routing table is exactly the kind of thing that drifts, so both now
 * resolve here.
 *
 * <h2>These are constants, not configuration</h2>
 *
 * A URL path is part of the published API contract, not an environment
 * specific value, so it does not belong in YAML. The one genuinely
 * configurable piece — the prefix the internal auth filter guards — lives in
 * {@code internal.api.path-prefix} and is cross-checked against
 * {@link #INTERNAL} at startup.
 *
 * <h2>Frozen paths</h2>
 *
 * {@link #TEMPLATES} + {@link #TEMPLATE_BY_ID} is consumed by the Messaging
 * Service on the message-send path. It is a live contract: the path, the
 * {@code X-Project-Id} header it requires, and the snake_case response
 * envelope are all fixed. Do not rename, re-shape or re-version it without
 * coordinating a migration with that service first.
 */
public final class ApiPaths {

    /** Public, versioned API surface. */
    public static final String API_V1 = "/api/v1";

    /** Service-to-service surface. Must match {@code internal.api.path-prefix}. */
    public static final String INTERNAL = "/internal";

    /** Versioned internal surface. */
    public static final String INTERNAL_V1 = INTERNAL + "/v1";

    // -- Public resources ---------------------------------------------

    /** Template collection root. */
    public static final String TEMPLATES = API_V1 + "/templates";

    /**
     * Single template by internal id, relative to {@link #TEMPLATES}.
     *
     * <p>FROZEN - consumed by the Messaging Service. See the class Javadoc.
     */
    public static final String TEMPLATE_BY_ID = "/{templateId}";

    /** Lookup by natural key (name + language) within a WABA. */
    public static final String TEMPLATE_LOOKUP = "/lookup";

    /** Paginated, filterable listing for the calling project. */
    public static final String TEMPLATE_LIST = "/my-templates";

    /** Update a template that has not yet been submitted to Meta. */
    public static final String TEMPLATE_DRAFT = "/{templateId}/draft";

    /** Submit an existing draft to Meta for review. */
    public static final String TEMPLATE_SUBMIT = "/{templateId}/submit";

    /** Pull templates from Meta into the local store. */
    public static final String TEMPLATE_SYNC = "/sync";

    /** Resumable header-media upload to Meta. */
    public static final String TEMPLATE_MEDIA = "/media";

    // -- Internal resources -------------------------------------------

    /** Service-to-service template reads. */
    public static final String INTERNAL_TEMPLATES = INTERNAL_V1 + "/templates";

    private ApiPaths() {
    }
}
