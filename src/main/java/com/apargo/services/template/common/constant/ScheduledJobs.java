package com.apargo.services.template.common.constant;

/**
 * Names of this service's scheduled jobs. Each is the {@code jobName} in logs
 * and the audit actor ({@code SYSTEM / <name>}) of everything the job does,
 * so it must stay stable.
 */
public final class ScheduledJobs {

    private ScheduledJobs() {
    }

    /** Settles templates stuck in SUBMITTED ({@code TemplateReconcileScheduler}). */
    public static final String TEMPLATE_RECONCILER = "template-reconciler";

    /** Deletes expired idempotency keys ({@code IdempotencyStore#purgeExpired}). */
    public static final String IDEMPOTENCY_PURGE = "idempotency-purge";
}
