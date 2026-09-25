package com.aigreentick.services.template.domain.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Statuses of a template that does NOT exist on Meta: never submitted
     * (DRAFT) or definitively not accepted (FAILED). Every other status is
     * "live" and holds its (waba, name, language) - see
     * {@code WhatsappTemplate.liveFlag} / {@code uk_waba_template_live}, whose
     * SQL expression must list exactly these values.
     */
    public static final Set<TemplateStatus> NOT_LIVE =
            Collections.unmodifiableSet(EnumSet.of(DRAFT, FAILED));

    public boolean isLive() {
        return !NOT_LIVE.contains(this);
    }

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