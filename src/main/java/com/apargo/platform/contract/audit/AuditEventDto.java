package com.apargo.platform.contract.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.apargo.platform.contract.event.EventIds;
import com.apargo.platform.contract.event.EventSchemaVersion;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One audit event, published as JSON to the audit topic
 * ({@code audit.topics.audit}, default {@code platform.audit.events}).
 *
 * <p>JSON is camelCase, instants are ISO-8601 UTC, and absent optional fields
 * are omitted. {@code changes} and {@code metadata} are always present
 * ({@code []} / {@code {}} when empty). See {@link AuditEventValidator} for
 * the required fields.
 *
 * <p>Build with {@link #builder()}: it assigns a UUIDv7 {@code eventId},
 * {@code schemaVersion} and {@code occurredAt} unless they are set explicitly.
 * The event id is fixed from then on and reused on every retry.
 *
 * <p>Versioning: adding an optional field keeps {@link #schemaVersion()};
 * adding a required field, or changing or removing any field, increments it.
 * New {@code eventType} and {@code module} values are never breaking.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"eventId", "schemaVersion", "sourceService", "environment", "module", "eventType",
        "status", "orgId", "projectId", "actor", "entity", "changes", "metadata", "error", "channel",
        "requestId", "traceId", "ip", "userAgent", "occurredAt"})
public record AuditEventDto(
        String eventId,
        Integer schemaVersion,
        String sourceService,
        String environment,
        String module,
        String eventType,
        AuditEventStatus status,
        Long orgId,
        Long projectId,
        AuditActorDto actor,
        AuditEntityDto entity,
        List<AuditChangeDto> changes,
        Map<String, Object> metadata,
        AuditErrorDto error,
        AuditChannel channel,
        String requestId,
        String traceId,
        String ip,
        String userAgent,
        Instant occurredAt) {

    /** {@code orgId} for platform-level data that belongs to no tenant. */
    public static final long PLATFORM_ORG_ID = 0L;

    public AuditEventDto {
        changes = changes == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(changes));
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder; not thread-safe. */
    public static final class Builder {

        private String eventId;
        private Integer schemaVersion;
        private String sourceService;
        private String environment;
        private String module;
        private String eventType;
        private AuditEventStatus status;
        private Long orgId;
        private Long projectId;
        private AuditActorDto actor;
        private AuditEntityDto entity;
        private final List<AuditChangeDto> changes = new ArrayList<>();
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private AuditErrorDto error;
        private AuditChannel channel;
        private String requestId;
        private String traceId;
        private String ip;
        private String userAgent;
        private Instant occurredAt;

        private Builder() {
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder schemaVersion(Integer schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder sourceService(String sourceService) {
            this.sourceService = sourceService;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder module(String module) {
            this.module = module;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder status(AuditEventStatus status) {
            this.status = status;
            return this;
        }

        public Builder orgId(Long orgId) {
            this.orgId = orgId;
            return this;
        }

        public Builder projectId(Long projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder actor(AuditActorDto actor) {
            this.actor = actor;
            return this;
        }

        public Builder entity(AuditEntityDto entity) {
            this.entity = entity;
            return this;
        }

        public Builder changes(List<AuditChangeDto> changes) {
            this.changes.clear();
            if (changes != null) {
                this.changes.addAll(changes);
            }
            return this;
        }

        public Builder change(String field, Object oldValue, Object newValue) {
            this.changes.add(new AuditChangeDto(field, oldValue, newValue));
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.clear();
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        /** Adds one metadata entry; a {@code null} value is skipped. */
        public Builder metadata(String key, Object value) {
            if (value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public Builder error(AuditErrorDto error) {
            this.error = error;
            return this;
        }

        public Builder channel(AuditChannel channel) {
            this.channel = channel;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder ip(String ip) {
            this.ip = ip;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public AuditEventDto build() {
            return new AuditEventDto(
                    eventId != null ? eventId : EventIds.newEventId(),
                    schemaVersion != null ? schemaVersion : EventSchemaVersion.AUDIT,
                    sourceService, environment, module, eventType, status, orgId, projectId,
                    actor, entity, changes, metadata, error, channel, requestId, traceId, ip, userAgent,
                    occurredAt != null ? occurredAt : Instant.now().truncatedTo(ChronoUnit.MILLIS));
        }
    }
}
