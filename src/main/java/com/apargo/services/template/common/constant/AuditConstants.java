package com.apargo.services.template.common.constant;

/**
 * Every fixed value this service writes into an audit event: module, entity
 * types, changed-field names, metadata keys, audit-only error codes and
 * messages. {@code TemplateAuditEvents} builds events from these only.
 *
 * <p>These names are part of the audit contract that consumers query on.
 * Renaming one is a breaking change for anyone reading the audit trail;
 * add new names instead.
 */
public final class AuditConstants {

    private AuditConstants() {
    }

    /** {@code module} of every event this service publishes. */
    public static final String MODULE = "TEMPLATE";

    /** Longest free text kept in an event (messages, changed text values). */
    public static final int MAX_TEXT_LENGTH = 500;

    /** Appended to text cut at {@link #MAX_TEXT_LENGTH}. */
    public static final String TRUNCATION_SUFFIX = "...";

    /** {@code actor.id} when a call carries neither a user nor a known calling service. */
    public static final String UNKNOWN_CALLER = "unknown";

    /** {@code entity.type} values. */
    public static final class EntityTypes {

        private EntityTypes() {
        }

        public static final String TEMPLATE = "TEMPLATE";
        public static final String SYSTEM_TEMPLATE = "SYSTEM_TEMPLATE";
    }

    /** {@code changes[].field} names. */
    public static final class Fields {

        private Fields() {
        }

        public static final String NAME = "name";
        public static final String LANGUAGE = "language";
        public static final String CATEGORY = "category";
        public static final String WABA_ID = "wabaId";
        public static final String STATUS = "status";
        public static final String META_TEMPLATE_ID = "metaTemplateId";
        public static final String REJECTION_REASON = "rejectionReason";
        public static final String DESCRIPTION = "description";
        public static final String SAMPLE_MEDIA_URL = "sampleMediaUrl";
        public static final String ACTIVE = "active";
    }

    /** {@code metadata} keys. Values are ids, enum names, counts or flags only. */
    public static final class Metadata {

        private Metadata() {
        }

        public static final String WABA_ID = "wabaId";
        public static final String STATUS = "status";
        public static final String CATEGORY = "category";
        public static final String LANGUAGE = "language";
        public static final String ACTIVE = "active";
        public static final String META_TEMPLATE_ID = "metaTemplateId";
        public static final String META_OUTCOME = "metaOutcome";
        public static final String META_DELETION = "metaDeletion";
        public static final String CONTENT_REPLACED = "contentReplaced";
        public static final String PAYLOAD_CHANGED = "payloadChanged";
        public static final String DELETED_COUNT = "deletedCount";
        public static final String INSERTED = "inserted";
        public static final String UPDATED = "updated";
        public static final String DELETED = "deleted";
    }

    /**
     * {@code error.code} values that exist only in audit events. They are not
     * {@code ErrorCode}s because no HTTP response carries them: a Meta
     * rejection is a 2xx, and the reconciler has no caller.
     */
    public static final class ErrorCodes {

        private ErrorCodes() {
        }

        /** Meta definitively rejected the submission. */
        public static final String META_REJECTED = "META_REJECTED";

        /** The reconciler gave up: Meta never received the submission. */
        public static final String SUBMISSION_NOT_RECEIVED = "SUBMISSION_NOT_RECEIVED";
    }

    /** {@code error.message} texts. {@code %s} is the error code. */
    public static final class Messages {

        private Messages() {
        }

        public static final String SUBMISSION_NOT_RECEIVED =
                "Meta has no template with this name; the submission never reached Meta.";
        public static final String UPSTREAM_FAILED = "An upstream service failed (%s)";
        public static final String UPSTREAM_TIMEOUT = "An upstream service timed out";
        public static final String ACTION_FAILED = "The action failed (%s)";
        public static final String UNEXPECTED_FAILURE = "The action failed unexpectedly";
    }
}
