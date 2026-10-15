package com.apargo.services.template.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.apargo.services.template.infrastructure.config.properties.TemplateWebhookEventsProperties;
import com.apargo.services.template.infrastructure.kafka.NonRetryableTemplateEventException;

import lombok.extern.slf4j.Slf4j;

/**
 * Error handling for {@code TemplateWebhookListener}. The container itself is
 * Boot's default ({@code spring.kafka.consumer.*} / {@code spring.kafka.listener.*}),
 * which picks up this {@link CommonErrorHandler} bean.
 *
 * <p>No dead-letter topic: the lane is low volume, and a record that still
 * fails after the retries is logged with its offset and skipped. A manual
 * sync restores the state it carried.
 */
@Slf4j
@Configuration
public class TemplateWebhookConsumerConfig {

    @Bean
    public CommonErrorHandler templateWebhookErrorHandler(TemplateWebhookEventsProperties properties) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> log.error("[META-EVENT] record skipped after retries topic={} partition={} offset={}",
                        record.topic(), record.partition(), record.offset(), ex),
                new FixedBackOff(properties.getRetryBackoff().toMillis(), properties.getRetryAttempts()));
        handler.addNotRetryableExceptions(NonRetryableTemplateEventException.class);
        return handler;
    }
}
