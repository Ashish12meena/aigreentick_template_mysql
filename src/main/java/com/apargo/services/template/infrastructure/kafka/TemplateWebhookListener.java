package com.apargo.services.template.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.apargo.services.template.application.service.TemplateMetaEventService;
import com.apargo.services.template.application.service.TemplateMetaEventService.Outcome;
import com.apargo.services.template.common.constant.MetaGraph;
import com.apargo.services.template.common.constant.ScheduledJobs;
import com.apargo.services.template.common.logging.ScheduledJobContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes {@code whatsapp.webhook.template}: Meta's template status, quality
 * and category webhooks, published by {@code webhook-service}. Without this,
 * a template stays PENDING after Meta approves it until someone runs a sync.
 *
 * <p>Thin on purpose: parse, route by {@code field}, hand to
 * {@link TemplateMetaEventService}. {@code message_template_components_update}
 * and anything else is acknowledged and ignored.
 *
 * <p>Errors: an unparseable record is skipped at once
 * ({@link NonRetryableTemplateEventException}); anything else is retried by
 * {@code TemplateWebhookConsumerConfig}'s error handler, then logged and
 * skipped. Manual sync is the fallback for a skipped record.
 */
@Slf4j
@Component
public class TemplateWebhookListener {

    /**
     * A private mapper, not the context one: the envelope is camelCase and
     * Meta adds fields without notice, so neither the service's naming nor its
     * unknown-property setting may apply here.
     */
    private static final ObjectReader READER = new ObjectMapper()
            .readerFor(TemplateWebhookEnvelope.class)
            .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final TemplateMetaEventService eventService;

    public TemplateWebhookListener(TemplateMetaEventService eventService) {
        this.eventService = eventService;
    }

    @KafkaListener(
            topics = "${template.webhook-events.topic}",
            groupId = "${template.webhook-events.group-id}",
            autoStartup = "${template.webhook-events.enabled}")
    public void onTemplateEvent(ConsumerRecord<String, String> record) {
        // Audited as SYSTEM / template-webhook-consumer.
        ScheduledJobContext.run(ScheduledJobs.TEMPLATE_WEBHOOK_CONSUMER, () -> handle(record));
    }

    private void handle(ConsumerRecord<String, String> record) {
        TemplateWebhookEnvelope envelope;
        try {
            envelope = READER.readValue(record.value());
        } catch (Exception e) {
            throw new NonRetryableTemplateEventException(
                    "Unparseable template webhook at partition " + record.partition()
                            + " offset " + record.offset(), e);
        }

        JsonNode value = envelope == null ? null : envelope.value();
        String field = envelope == null ? null : envelope.resolvedField();
        if (value == null || field == null) {
            log.warn("[META-EVENT] record without field/value skipped partition={} offset={}",
                    record.partition(), record.offset());
            return;
        }

        String metaTemplateId = text(value, MetaGraph.Webhook.MESSAGE_TEMPLATE_ID);
        if (metaTemplateId == null) {
            log.warn("[META-EVENT] {} without message_template_id skipped eventId={}", field, envelope.eventId());
            return;
        }

        Outcome outcome = switch (field) {
            case MetaGraph.Webhook.FIELD_STATUS_UPDATE -> eventService.applyStatus(
                    envelope.providerWabaId(),
                    metaTemplateId,
                    text(value, MetaGraph.Webhook.MESSAGE_TEMPLATE_NAME),
                    text(value, MetaGraph.Webhook.MESSAGE_TEMPLATE_LANGUAGE),
                    text(value, MetaGraph.Webhook.EVENT),
                    text(value, MetaGraph.Webhook.REASON),
                    text(value.path(MetaGraph.Webhook.OTHER_INFO), MetaGraph.Webhook.DESCRIPTION));
            case MetaGraph.Webhook.FIELD_QUALITY_UPDATE -> eventService.applyQuality(
                    metaTemplateId, text(value, MetaGraph.Webhook.NEW_QUALITY_SCORE));
            case MetaGraph.Webhook.FIELD_CATEGORY_UPDATE -> {
                String newCategory = text(value, MetaGraph.Webhook.NEW_CATEGORY);
                // Without new_category it is Meta's advance notice; the change itself follows.
                yield newCategory == null ? null : eventService.applyCategory(metaTemplateId, newCategory);
            }
            default -> null;
        };

        if (outcome == Outcome.AWAITING_SUBMISSION) {
            // Retried by the error handler until the submission stores Meta's id.
            throw new IllegalStateException("Template metaId=" + metaTemplateId
                    + " is still being submitted; retrying " + field);
        }
        log.debug("[META-EVENT] {} metaId={} -> {} eventId={}", field, metaTemplateId, outcome, envelope.eventId());
    }

    private static String text(JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(key);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }
}
