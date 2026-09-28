package com.apargo.services.template.infrastructure.audit;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.apargo.services.template.common.logging.TraceParent;
import com.apargo.services.template.infrastructure.config.AuditPublisherConfig;
import com.apargo.services.template.infrastructure.config.properties.AuditProperties;
import com.apargo.platform.contract.audit.AuditEventDto;
import com.apargo.platform.contract.audit.AuditEventValidator;
import com.apargo.platform.contract.event.EventTopics;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.extern.slf4j.Slf4j;

/**
 * The only class that talks to Kafka (Audit Producer Guide §1, §6).
 *
 * <h2>When</h2>
 * {@code @TransactionalEventListener(AFTER_COMMIT)}: an event raised inside a
 * transaction is published only once that transaction has committed, and is
 * dropped if it rolls back. {@code fallbackExecution = true} publishes events
 * raised outside any transaction straight away - the Meta submission flow and
 * the reconciler raise theirs after their own short transactions committed.
 *
 * <h2>How</h2>
 * The event is serialized and validated on the calling thread, then handed to
 * the {@code auditPublisherExecutor} pool, which calls the non-blocking
 * {@code KafkaTemplate.send}. The request never waits for Kafka and a send
 * failure never reaches the business request.
 *
 * <h2>When it fails</h2>
 * The producer retries (idempotent, {@code acks=all}) until
 * {@code delivery.timeout.ms}. Anything that still loses the event - send
 * failure, full queue, invalid event - is logged at ERROR with the
 * {@code eventId} and the full event JSON, which is safe to log (events carry
 * no secrets) and can be replayed as-is: the key and {@code eventId} are
 * unchanged, and the audit service de-duplicates on them.
 */
@Slf4j
@Component
public class AuditEventPublisher {

    /**
     * Private mapper, not a Spring bean (rules §5.5): camelCase, ISO-8601
     * instants, absent fields omitted.
     */
    private static final ObjectMapper JSON = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AuditProperties properties;
    private final ThreadPoolTaskExecutor executor;

    public AuditEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            AuditProperties properties,
            @Qualifier(AuditPublisherConfig.AUDIT_PUBLISHER_EXECUTOR) ThreadPoolTaskExecutor executor) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuditEvent(AuditEventDto event) {
        String json;
        try {
            json = JSON.writeValueAsString(event);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.error("Audit event not published: serialization failed eventId={} eventType={} event={}",
                    event.eventId(), event.eventType(), event, ex);
            return;
        }

        List<String> problems = AuditEventValidator.validate(event);
        if (!problems.isEmpty()) {
            log.error("Audit event not published: invalid eventId={} problems={} event={}",
                    event.eventId(), problems, json);
            return;
        }

        if (!properties.isEnabled()) {
            log.debug("Audit publishing disabled; event={}", json);
            return;
        }

        try {
            executor.execute(() -> send(event, json));
        } catch (TaskRejectedException ex) {
            log.error("Audit event not published: publisher queue full eventId={} event={}",
                    event.eventId(), json);
        } catch (RuntimeException ex) {
            log.error("Audit event not published: hand-off failed eventId={} event={}",
                    event.eventId(), json, ex);
        }
    }

    private void send(AuditEventDto event, String json) {
        try {
            RecordHeaders headers = new RecordHeaders();
            header(headers, EventTopics.HEADER_EVENT_ID, event.eventId());
            header(headers, EventTopics.HEADER_EVENT_TYPE, event.eventType());
            header(headers, EventTopics.HEADER_SCHEMA_VERSION, String.valueOf(event.schemaVersion()));
            header(headers, EventTopics.HEADER_SOURCE_SERVICE, event.sourceService());
            header(headers, EventTopics.HEADER_TRACEPARENT, TraceParent.format(event.traceId()));

            ProducerRecord<String, String> record = new ProducerRecord<>(
                    properties.getTopics().getAudit(), null, event.eventId(), json, headers);

            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Audit event not delivered eventId={} eventType={} event={}",
                            event.eventId(), event.eventType(), json, ex);
                } else if (log.isDebugEnabled()) {
                    log.debug("Audit event delivered eventId={} eventType={} offset={}",
                            event.eventId(), event.eventType(), result.getRecordMetadata().offset());
                }
            });
        } catch (RuntimeException ex) {
            // send() itself throws when metadata is unavailable past max.block.ms,
            // or when the producer is closed during shutdown.
            log.error("Audit event not delivered eventId={} eventType={} event={}",
                    event.eventId(), event.eventType(), json, ex);
        }
    }

    private static void header(RecordHeaders headers, String name, String value) {
        if (value != null) {
            headers.add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
