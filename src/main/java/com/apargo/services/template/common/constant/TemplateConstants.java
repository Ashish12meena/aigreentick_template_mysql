package com.apargo.services.template.common.constant;

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

    /**
     * Error-response {@code message} texts ({@code GlobalExceptionHandler},
     * {@code ApiErrorController}). Written for API clients: never SQL, paths,
     * stack traces or secrets. {@code %s} placeholders are filled with the
     * offending name or value.
     */
    public static final class ErrorMessages {

        private ErrorMessages() {
        }

        public static final String VALIDATION_FAILED = "Request has invalid fields";
        public static final String INVALID_HEADERS = "Invalid header %s";
        public static final String HEADER_PROBLEM = "'%s' %s";
        public static final String HEADER_PROBLEM_SEPARATOR = "; ";
        public static final String INVALID_HEADER_TYPE = "Invalid header '%s': expected %s";
        public static final String DIFFERENT_TYPE = "a different type";
        public static final String REQUIRED = "%s is required";
        public static final String MUST_BE_ONE_OF = "%s must be one of %s";
        public static final String INVALID_VALUE = "%s has an invalid value";
        public static final String UNREADABLE_BODY = "Request body is missing or is not valid JSON";
        public static final String MISSING_HEADER = "Missing required header '%s'";
        public static final String NO_ENDPOINT = "No endpoint exists for this path";
        public static final String METHOD_NOT_SUPPORTED = "HTTP method '%s' is not supported for this endpoint";
        public static final String PAYLOAD_TOO_LARGE = "File size exceeds the maximum allowed limit";
        public static final String UNSUPPORTED_CONTENT_TYPE = "Content-Type '%s' is not supported; use %s";
        public static final String DUPLICATE_RESOURCE = "A resource with the same identifier already exists";
        public static final String WABA_CREDENTIALS_UNAVAILABLE = "Could not resolve WhatsApp Business Account credentials";
        public static final String DEPENDENCY_TIMEOUT = "A dependent service did not respond in time";
        public static final String DEPENDENCY_FAILURE = "A dependent service failed";
        public static final String UNEXPECTED = "An unexpected error occurred. Please try again later.";

        /** Stand-in when a parameter's name cannot be determined. */
        public static final String UNNAMED_PARAMETER = "parameter";
    }

    /**
     * Reasons stored in {@code rejectionReason} when a template ends FAILED
     * without a message from Meta. Shown to users with the template.
     */
    public static final class RejectionReasons {

        private RejectionReasons() {
        }

        public static final String CREDENTIALS_UNAVAILABLE =
                "Could not resolve WABA credentials; template was not sent to Meta";
        public static final String META_REJECTED = "Meta rejected the template";

        /** {@code %d}: minutes waited before giving up. */
        public static final String NOT_RECEIVED_BY_META =
                "Submission was not received by Meta (no template with this name found on Meta "
                        + "after %d minutes). It is safe to create it again.";
    }
}
