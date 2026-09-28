package com.apargo.services.template.common.constant;

/**
 * Names defined by Meta's Graph API (WhatsApp Business Management): path
 * segments, query parameters, headers and response fields. Meta owns these
 * spellings (snake_case); keeping them here means a Meta rename is a
 * one-line change and a typo cannot hide in a parser.
 *
 * <p>Base URL and API version are configuration
 * ({@code facebook-service.base-url}, {@code facebook-service.api-version}),
 * not constants.
 */
public final class MetaGraph {

    private MetaGraph() {
    }

    /** Path segments after {@code /{version}/{id}/}. */
    public static final class Paths {

        private Paths() {
        }

        /** {@code /{wabaId}/message_templates}: create, list, delete. */
        public static final String MESSAGE_TEMPLATES = "message_templates";

        /** {@code /{appId}/uploads}: start a resumable upload session. */
        public static final String UPLOADS = "uploads";
    }

    /** Query parameters. */
    public static final class Params {

        private Params() {
        }

        public static final String STATUS = "status";
        public static final String LANGUAGE = "language";
        public static final String CATEGORY = "category";
        public static final String NAME = "name";
        public static final String LIMIT = "limit";
        public static final String AFTER = "after";
        public static final String FILE_NAME = "file_name";
        public static final String FILE_LENGTH = "file_length";
        public static final String FILE_TYPE = "file_type";
        public static final String ACCESS_TOKEN = "access_token";
    }

    /** Request headers specific to Meta. */
    public static final class Headers {

        private Headers() {
        }

        /** Byte offset for a resumable upload chunk. */
        public static final String FILE_OFFSET = "file_offset";

        /** {@code Authorization} scheme for the resumable upload endpoints. */
        public static final String OAUTH_SCHEME = "OAuth ";
    }

    /** Response body fields. */
    public static final class Fields {

        private Fields() {
        }

        public static final String ID = "id";
        public static final String NAME = "name";
        public static final String LANGUAGE = "language";
        public static final String STATUS = "status";
        public static final String CATEGORY = "category";
        public static final String DATA = "data";
        public static final String PAGING = "paging";
        public static final String NEXT = "next";
        public static final String CURSORS = "cursors";
        public static final String AFTER = "after";
        public static final String ERROR = "error";
        public static final String ERROR_USER_MSG = "error_user_msg";
        public static final String ERROR_USER_TITLE = "error_user_title";
        public static final String MESSAGE = "message";
    }
}
