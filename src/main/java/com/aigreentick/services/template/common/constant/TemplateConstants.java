package com.aigreentick.services.template.common.constant;

/**
 * Human-readable response text and pagination defaults.
 *
 * <p>Pagination follows API Standard §5: {@code page} from 0 (default 0),
 * {@code size} 1–100 (default 20), {@code sort} from a documented whitelist
 * (default {@code createdAt}), {@code order} {@code asc|desc} (default
 * {@code desc}).
 */
public final class TemplateConstants {

    private TemplateConstants() {
    }

    /** Pagination defaults, shared by the controller and the query layer. */
    public static final class Defaults {

        private Defaults() {
        }

        public static final String PAGE = "0";
        public static final String SIZE = "20";
        public static final int MAX_SIZE = 100;
        public static final String SORT = SortFields.CREATED_AT;
        public static final String ORDER = "desc";
    }

    /**
     * Fields the template list may be sorted by. Anything else is rejected
     * with 422 before it reaches the query (an unknown property used to
     * fail inside JPA as a 500).
     */
    public static final class SortFields {

        private SortFields() {
        }

        public static final String CREATED_AT = "createdAt";
        public static final String UPDATED_AT = "updatedAt";
        public static final String NAME = "name";
        public static final String STATUS = "status";
        public static final String CATEGORY = "category";
        public static final String LANGUAGE = "language";
    }

    /** Response messages. Wording is not part of the API contract. */
    public static final class Messages {

        private Messages() {
        }

        public static final String TEMPLATE_FETCHED = "Template fetched successfully";
        public static final String TEMPLATES_FETCHED = "Templates fetched successfully";
        public static final String TEMPLATE_CREATED = "Template created successfully";
        public static final String DRAFT_SAVED = "Draft template saved successfully";
        public static final String TEMPLATE_SUBMITTED = "Template submitted to Meta";
        public static final String DRAFT_UPDATED = "Draft updated successfully";
        public static final String TEMPLATES_DELETED = "Templates deleted successfully";
        public static final String MEDIA_UPLOADED = "Media uploaded successfully";
        public static final String SYNC_ACCEPTED = "Template sync started in the background";
        public static final String SYSTEM_TEMPLATE_FETCHED = "Library template fetched successfully";
        public static final String SYSTEM_TEMPLATES_FETCHED = "Library templates fetched successfully";
        public static final String SYSTEM_TEMPLATE_CREATED = "Library template created successfully";
        public static final String SYSTEM_TEMPLATE_UPDATED = "Library template updated successfully";

        /**
         * The template was saved (it has an id) but Meta did not accept it.
         * The request still succeeded from the client's point of view: see
         * {@code data.status} and {@code data.errorMessage}.
         */
        public static final String CREATED_META_REJECTED = "Template saved, but Meta did not accept it: %s";
        public static final String SUBMIT_META_REJECTED = "Template was not accepted by Meta: %s";
    }
}
