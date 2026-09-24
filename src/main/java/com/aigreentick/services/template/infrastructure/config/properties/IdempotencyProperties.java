package com.aigreentick.services.template.infrastructure.config.properties;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code X-Idempotency-Key} handling for endpoints marked {@code @Idempotent}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "idempotency")
public class IdempotencyProperties {

    /**
     * Reject a create/send request without the header (400). The standard
     * makes the header required on these APIs; switch off only while a
     * client is being migrated.
     */
    private boolean required = true;

    /** How long a completed response is kept for replay. */
    @NotNull
    private Duration ttl = Duration.ofHours(24);

    /**
     * After this long an in-progress entry is treated as abandoned (the
     * instance died mid-request) and the key can be used again.
     */
    @NotNull
    private Duration inProgressTimeout = Duration.ofMinutes(5);
}
