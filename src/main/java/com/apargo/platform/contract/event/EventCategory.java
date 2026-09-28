package com.apargo.platform.contract.event;

/**
 * Which event stream a message belongs to. Each category has its own topic
 * and DTO.
 */
public enum EventCategory {

    /** Business changes and business failures: {@code AuditEventDto}, audit topic. */
    AUDIT,

    /** Sign-in, sign-out and gateway access decisions: {@code AccessEventDto}, access topic. */
    ACCESS
}
