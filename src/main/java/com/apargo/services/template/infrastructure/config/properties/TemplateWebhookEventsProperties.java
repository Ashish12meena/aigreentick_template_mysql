package com.apargo.services.template.infrastructure.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

/**
 * Consumer of Meta's template webhooks - bound to {@code template.webhook-events.*}.
 * {@code webhook-service} publishes them to {@code whatsapp.webhook.template};
 * {@code TemplateWebhookListener} applies them.
 *
 * <p>{@code enabled}, {@code topic} and {@code group-id} are read by
 * {@code @KafkaListener} placeholders; declared here so they are validated.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "template.webhook-events")
public class TemplateWebhookEventsProperties {

    /** Off for local runs without a broker; on in every shared environment. */
    private boolean enabled = true;

    @NotBlank
    private String topic = "whatsapp.webhook.template";

    @NotBlank
    private String groupId = "template-service.webhook-template";

    /**
     * Retries of a failed record before it is logged and skipped. Covers a
     * brief DB outage and an approval that arrives before Meta's id is stored.
     */
    @PositiveOrZero
    private long retryAttempts = 6;

    @NotNull
    private Duration retryBackoff = Duration.ofSeconds(10);
}
