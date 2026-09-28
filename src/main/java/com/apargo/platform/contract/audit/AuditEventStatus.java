package com.apargo.platform.contract.audit;

/**
 * Outcome of one audited action attempt.
 *
 * <p>Describes the business action only. Kafka or audit-store delivery
 * problems never change it.
 */
public enum AuditEventStatus {
    SUCCESS,
    FAILURE
}
