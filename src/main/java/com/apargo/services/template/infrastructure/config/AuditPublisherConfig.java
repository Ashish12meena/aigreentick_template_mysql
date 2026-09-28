package com.apargo.services.template.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.apargo.services.template.common.logging.MdcTaskDecorator;
import com.apargo.services.template.infrastructure.config.properties.AuditProperties;

import lombok.RequiredArgsConstructor;

/**
 * Pool that hands audit events to the Kafka producer.
 *
 * <h2>Why events are not sent on the request thread</h2>
 * {@code KafkaTemplate.send} is asynchronous for delivery, but it blocks the
 * caller while it fetches broker metadata (up to {@code max.block.ms}) - on
 * the first send, and whenever the broker is unreachable. On a request thread
 * that would make the API response wait for Kafka, which the Producer Guide
 * forbids. Here only this pool waits.
 *
 * <p>No {@code CallerRunsPolicy}: when the queue is full the event is refused
 * (and logged with its full JSON by {@code AuditEventPublisher}) rather than
 * pushed back onto the request thread.
 *
 * <p>{@code @DependsOn(KAFKA_PRODUCER_FACTORY)} makes Spring shut this pool
 * down - draining queued events - before Spring Boot's producer factory
 * closes the producer.
 */
@Configuration
@RequiredArgsConstructor
public class AuditPublisherConfig {

    /** Bean name referenced by {@code @Qualifier} at the injection site. */
    public static final String AUDIT_PUBLISHER_EXECUTOR = "auditPublisherExecutor";

    /** Spring Boot's auto-configured Kafka producer factory ({@code KafkaAutoConfiguration}). */
    public static final String KAFKA_PRODUCER_FACTORY = "kafkaProducerFactory";

    private final AuditProperties properties;

    @Bean(name = AUDIT_PUBLISHER_EXECUTOR)
    @DependsOn(KAFKA_PRODUCER_FACTORY)
    public ThreadPoolTaskExecutor auditPublisherExecutor() {
        AuditProperties.Publisher publisher = properties.getPublisher();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(publisher.getPoolSize());
        executor.setMaxPoolSize(publisher.getPoolSize());
        executor.setQueueCapacity(publisher.getQueueCapacity());
        executor.setThreadNamePrefix(publisher.getThreadNamePrefix());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(publisher.getAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }
}
