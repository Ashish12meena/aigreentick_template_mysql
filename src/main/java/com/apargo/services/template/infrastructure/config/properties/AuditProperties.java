package com.apargo.services.template.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.apargo.platform.contract.event.EventEnvironments;
import com.apargo.platform.contract.event.EventTopics;
import com.apargo.services.template.common.constant.InternalHeaders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Audit event settings - bound to {@code audit.*} (Audit Producer Guide §2).
 * Kafka connection and producer settings are Spring Boot's own
 * {@code spring.kafka.*}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "audit")
public class AuditProperties {

    /**
     * When false, events are still built but only logged at DEBUG, never
     * sent. Local runs without a broker only; production forces it on.
     */
    private boolean enabled = true;

    /** {@code sourceService} on every event. */
    @NotBlank
    private String sourceService = InternalHeaders.THIS_SERVICE;

    /** {@code environment} on every event: one of {@link EventEnvironments#ALL}. */
    @NotBlank
    private String environment = EventEnvironments.DEVELOPMENT;

    @Valid
    private Topics topics = new Topics();

    @Valid
    private Publisher publisher = new Publisher();

    /** Fails startup on an unknown environment instead of publishing events the audit service rejects. */
    @AssertTrue(message = "audit.environment must be development, staging or production")
    public boolean isEnvironmentValid() {
        return EventEnvironments.isValid(environment);
    }

    @Getter
    @Setter
    public static class Topics {

        /** Audit topic ({@code audit.topics.audit}). */
        @NotBlank
        private String audit = EventTopics.DEFAULT_AUDIT_TOPIC;
    }

    /**
     * The pool that hands events to Kafka, so a request thread never waits on
     * the broker. Queue overflow drops the event with an ERROR log carrying
     * the full JSON for replay.
     */
    @Getter
    @Setter
    public static class Publisher {

        @Positive
        private int poolSize = 2;

        @Positive
        private int queueCapacity = 10_000;

        /** How long shutdown waits for queued events to be handed to the producer. */
        @Min(0)
        private int awaitTerminationSeconds = 15;

        /** Names the pool's threads in logs and thread dumps. */
        @NotBlank
        private String threadNamePrefix = "audit-publisher-";
    }
}
