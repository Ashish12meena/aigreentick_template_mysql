package com.apargo.platform.contract.event;

/**
 * Where events go and how each Kafka record is labelled.
 *
 * <p>Topic <em>names</em> are configuration, never code: services bind the
 * property keys below. The defaults here are what the platform provisions and
 * what a service should use as its configuration default.
 *
 * <p>Record layout for every event category:
 * <ul>
 *   <li>key: the {@code eventId}</li>
 *   <li>value: the DTO as UTF-8 JSON, camelCase</li>
 *   <li>headers: {@link #HEADER_EVENT_ID}, {@link #HEADER_EVENT_TYPE},
 *       {@link #HEADER_SCHEMA_VERSION}, {@link #HEADER_SOURCE_SERVICE},
 *       {@link #HEADER_TRACEPARENT}; values UTF-8</li>
 * </ul>
 */
public final class EventTopics {

    /** Property holding the audit topic name. */
    public static final String AUDIT_TOPIC_PROPERTY = "audit.topics.audit";

    /** Property holding the access topic name (auth / gateway only). */
    public static final String ACCESS_TOPIC_PROPERTY = "audit.topics.access";

    public static final String DEFAULT_AUDIT_TOPIC = "platform.audit.events";

    public static final String DEFAULT_ACCESS_TOPIC = "platform.access.events";

    public static final String HEADER_EVENT_ID = "eventId";

    public static final String HEADER_EVENT_TYPE = "eventType";

    public static final String HEADER_SCHEMA_VERSION = "schemaVersion";

    public static final String HEADER_SOURCE_SERVICE = "sourceService";

    /** W3C trace context of the action, so the consumer continues the same trace. */
    public static final String HEADER_TRACEPARENT = "traceparent";

    private EventTopics() {
    }
}
