package com.aigreentick.services.template.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled}. Currently one job: the hourly purge of expired
 * idempotency keys ({@code IdempotencyStore#purgeExpired}).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
