package com.apargo.platform.contract.event;

/**
 * Current schema version of each event contract. Bump only for a breaking
 * change (a new required field, or a changed or removed field).
 */
public final class EventSchemaVersion {

    /** {@code AuditEventDto}. */
    public static final int AUDIT = 1;

    /** {@code AccessEventDto}. */
    public static final int ACCESS = 1;

    private EventSchemaVersion() {
    }
}
