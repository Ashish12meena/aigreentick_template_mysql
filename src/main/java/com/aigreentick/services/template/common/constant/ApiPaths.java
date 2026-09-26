package com.aigreentick.services.template.common.constant;

/**
 * Every HTTP path this service exposes, in one place.
 *
 * <h2>These are constants, not configuration</h2>
 *
 * A URL path is part of the published API contract, not an environment
 * specific value, so it does not belong in YAML. The one genuinely
 * configurable piece — the prefix the internal auth filter guards — lives in
 * {@code internal.api.path-prefix} and is cross-checked against
 * {@link #INTERNAL} at startup.
 *
 * <h2>Consumed by the Messaging Service</h2>
 *
 * {@link #TEMPLATES} + {@link #TEMPLATE_BY_ID} (and its {@code /internal}
 * twin) is read on the message-send path. Do not rename or re-version it
 * without coordinating with that service.
 */
public final class ApiPaths {

    /** Public, versioned API surface. */
    public static final String API_V1 = "/api/v1";

    /** Service-to-service surface. Must match {@code internal.api.path-prefix}. */
    public static final String INTERNAL = "/internal";

    /** Versioned internal surface. */
    public static final String INTERNAL_V1 = INTERNAL + "/v1";

    /** Servlet error path; answered by {@code ApiErrorController} in the standard wrapper. */
    public static final String ERROR = "/error";

    // -- Public resources ---------------------------------------------

    /** Template collection root. */
    public static final String TEMPLATES = API_V1 + "/templates";

    /** Single template by internal id, relative to {@link #TEMPLATES}. */
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

    /** Template Library: system-level templates shared by every organization. */
    public static final String TEMPLATE_LIBRARY = API_V1 + "/template-library";

    /** Single library template by id, relative to {@link #TEMPLATE_LIBRARY} / {@link #INTERNAL_TEMPLATE_LIBRARY}. */
    public static final String SYSTEM_TEMPLATE_BY_ID = "/{systemTemplateId}";

    // -- Internal resources -------------------------------------------

    /** Service-to-service template reads. */
    public static final String INTERNAL_TEMPLATES = INTERNAL_V1 + "/templates";

    /** Template Library maintenance (create / update). Never exposed through the gateway. */
    public static final String INTERNAL_TEMPLATE_LIBRARY = INTERNAL_V1 + "/template-library";

    private ApiPaths() {
    }

    /** {@code Location} value for a created template: a path, so no internal host name leaks through a gateway. */
    public static String templateLocation(Long templateId) {
        return TEMPLATES + "/" + templateId;
    }

    /** {@code Location} value for a created library template: its public read path. */
    public static String systemTemplateLocation(Long systemTemplateId) {
        return TEMPLATE_LIBRARY + "/" + systemTemplateId;
    }
}
