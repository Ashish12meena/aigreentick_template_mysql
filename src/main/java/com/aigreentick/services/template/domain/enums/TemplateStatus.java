package com.aigreentick.services.template.domain.enums;

import java.util.Optional;

public enum TemplateStatus {
    DRAFT,
    NEW_CREATED,
    SUBMITTED,
    PENDING,
    APPROVED,
    REJECTED,
    PAUSED,
    DISABLED,
    FAILED,

    /**
     * Meta returned a status this service does not model. The original string is
     * kept in WhatsappTemplate.metaStatusRaw so a later sync can reconcile.
     *
     * Never throw on an unrecognised remote status: the throw happened inside the
     * creation transaction, so it rolled back a template Meta had already created.
     */
    UNKNOWN;

    /** Null-safe, case-insensitive, never throws. */
    public static Optional<TemplateStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TemplateStatus.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}