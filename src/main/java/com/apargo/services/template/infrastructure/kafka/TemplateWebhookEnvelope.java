package com.apargo.services.template.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The JSON value of one record on {@code whatsapp.webhook.template}, as
 * written by {@code webhook-service}. Our own copy of that contract, not a
 * dependency on its classes.
 *
 * <p>{@code payload} is Meta's whole {@code change} object, so the template
 * fields are at {@code payload.value.*} (Meta's snake_case). {@code value} is
 * kept as a tree because its keys differ by {@code field}. Any level can be
 * absent: the producer omits nulls.
 */
public record TemplateWebhookEnvelope(
        String eventId,
        String field,
        String providerWabaId,
        Payload payload) {

    public record Payload(String field, JsonNode value) {
    }

    /** {@code field} from the envelope, else from Meta's change object. */
    public String resolvedField() {
        if (field != null && !field.isBlank()) {
            return field;
        }
        return payload == null ? null : payload.field();
    }

    public JsonNode value() {
        return payload == null ? null : payload.value();
    }
}
