package com.aigreentick.services.template.common.constant;

/**
 * Human-readable response text and pagination defaults.
 *
 * <h2>What was removed</h2>
 *
 * This class used to also carry a {@code Paths} block (a second, unused
 * routing table that contradicted the controller) and a {@code Fields} block
 * labelled "Mongo DB Field Names" — a leftover from a datastore this service
 * does not use. Routing now lives in {@link ApiPaths}; the Mongo field names
 * are gone entirely.
 *
 * <p>What remains is genuinely shared: message strings that more than one
 * class emits, and the pagination defaults the controller and the query
 * service must agree on.
 */
public final class TemplateConstants {

    private TemplateConstants() {
    }

    /** Pagination defaults, shared by the controller and the query layer. */
    public static final class Defaults {

        private Defaults() {
        }

        public static final int PAGE = 0;
        public static final int SIZE = 10;
        public static final int MAX_SIZE = 100;
        public static final String SORT_BY = "createdAt";
        public static final String SORT_DIRECTION = "desc";
    }

    /** Response messages. Wording is not part of the API contract. */
    public static final class Messages {

        private Messages() {
        }

        public static final String TEMPLATE_FETCHED = "Template fetched";
        public static final String TEMPLATES_FETCHED = "Templates fetched";
        public static final String TEMPLATE_CREATED = "Template created successfully";
        public static final String TEMPLATE_SUBMITTED = "Template submitted to Meta";
        public static final String DRAFT_UPDATED = "Draft updated";
        public static final String TEMPLATE_DELETED = "Template deleted";
        public static final String MEDIA_UPLOADED = "Media uploaded";
        public static final String SYNC_ACCEPTED =
                "Template sync started in background. Poll the template list for completion.";
    }
}
