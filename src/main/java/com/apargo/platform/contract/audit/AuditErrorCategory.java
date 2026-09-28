package com.apargo.platform.contract.audit;

/**
 * Coarse class of a {@link AuditEventStatus#FAILURE}. The service's own
 * error code goes in {@link AuditErrorDto#code()}.
 */
public enum AuditErrorCategory {
    VALIDATION,
    BUSINESS,
    AUTHENTICATION,
    AUTHORIZATION,
    EXTERNAL_SERVICE,
    SYSTEM
}
