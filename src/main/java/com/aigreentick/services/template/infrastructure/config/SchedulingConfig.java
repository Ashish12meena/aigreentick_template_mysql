package com.aigreentick.services.template.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled}. Jobs: the hourly purge of expired idempotency
 * keys ({@code IdempotencyStore#purgeExpired}) and the settling of templates
 * stuck in SUBMITTED ({@code TemplateReconcileScheduler#run}). Both schedules
 * are configured in application.yaml, not in code.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
