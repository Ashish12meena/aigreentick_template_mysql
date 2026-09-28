package com.apargo.services.template.domain.enums;

/**
 * This service's audit event catalogue ({@code eventType}, module
 * {@code TEMPLATE}). Names are {@code <ENTITY>_<PAST_VERB>}; success or
 * failure is the event's {@code status}, never part of the name. Adding a
 * value is not a breaking change; renaming or removing one is.
 *
 * <p>Built only by {@code TemplateAuditEvents}; see {@code architecture.md}
 * §13 for which action raises which event.
 */
public enum TemplateAuditEventType {

    /** A template row was created (draft or for submission). */
    TEMPLATE_CREATED,

    /** A draft's content was replaced, or Meta changed only non-status details. */
    TEMPLATE_UPDATED,

    /** A submission to Meta was made and its outcome recorded (SUCCESS or FAILURE). */
    TEMPLATE_SUBMITTED,

    TEMPLATE_DELETED,

    /** Every template of a project was soft-deleted. */
    TEMPLATE_BULK_DELETED,

    // -- Status changes reported by Meta (sync or reconciliation) --
    TEMPLATE_APPROVED,
    TEMPLATE_REJECTED,
    TEMPLATE_PAUSED,
    TEMPLATE_DISABLED,

    /** Any other status change reported by Meta (e.g. SUBMITTED -> PENDING). */
    TEMPLATE_STATUS_CHANGED,

    /** Meta re-categorised a template without changing its status. */
    TEMPLATE_CATEGORY_CHANGED,

    /** A sync with Meta finished (SUCCESS with counts) or failed (FAILURE). */
    TEMPLATE_SYNCED,

    // -- Template Library (platform-level, orgId 0) --
    SYSTEM_TEMPLATE_CREATED,
    SYSTEM_TEMPLATE_UPDATED
}
