package com.apargo.platform.contract.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.apargo.platform.contract.event.EventEnvironments;
import com.apargo.platform.contract.event.EventIds;

/**
 * Checks an {@link AuditEventDto} against the producer contract before it is
 * published. Returns every problem rather than stopping at the first, so one
 * log line explains a broken event completely.
 *
 * <p>Producers call this in their publisher; an invalid event is a bug in the
 * producer's event factory, never a reason to fail the business request.
 */
public final class AuditEventValidator {

    /** {@code <ENTITY>_<PAST_VERB>}, upper snake case, at least two words. */
    private static final Pattern EVENT_TYPE = Pattern.compile("[A-Z][A-Z0-9]*(_[A-Z0-9]+)+");

    /** Upper snake case, e.g. {@code TEMPLATE}, {@code WABA}. */
    private static final Pattern UPPER_SNAKE = Pattern.compile("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*");

    /** W3C trace id: 32 lowercase hex, not all zeros. */
    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");

    private AuditEventValidator() {
    }

    public static List<String> validate(AuditEventDto event) {
        List<String> problems = new ArrayList<>();
        if (event == null) {
            problems.add("event is null");
            return problems;
        }

        if (!EventIds.isUuidV7(event.eventId())) {
            problems.add("eventId must be a UUIDv7");
        }
        if (event.schemaVersion() == null || event.schemaVersion() < 1) {
            problems.add("schemaVersion is required");
        }
        requireText(problems, "sourceService", event.sourceService());
        if (!EventEnvironments.isValid(event.environment())) {
            problems.add("environment must be one of " + EventEnvironments.ALL);
        }
        if (event.module() == null || !UPPER_SNAKE.matcher(event.module()).matches()) {
            problems.add("module is required, upper snake case");
        }
        if (event.eventType() == null || !EVENT_TYPE.matcher(event.eventType()).matches()) {
            problems.add("eventType is required, <ENTITY>_<PAST_VERB>");
        }
        if (event.status() == null) {
            problems.add("status is required");
        }
        if (event.orgId() == null || event.orgId() < 0) {
            problems.add("orgId is required (0 for platform-level data)");
        }

        AuditActorDto actor = event.actor();
        if (actor == null) {
            problems.add("actor is required");
        } else {
            if (actor.type() == null) {
                problems.add("actor.type is required");
            }
            requireText(problems, "actor.id", actor.id());
        }

        AuditEntityDto entity = event.entity();
        if (entity != null) {
            requireText(problems, "entity.type", entity.type());
            if ((entity.id() == null || entity.id().isBlank()) && event.status() != AuditEventStatus.FAILURE) {
                problems.add("entity.id is required unless status is FAILURE");
            }
        }

        for (int i = 0; i < event.changes().size(); i++) {
            AuditChangeDto change = event.changes().get(i);
            if (change == null || change.field() == null || change.field().isBlank()) {
                problems.add("changes[" + i + "].field is required");
            }
        }

        if (event.status() == AuditEventStatus.FAILURE) {
            AuditErrorDto error = event.error();
            if (error == null) {
                problems.add("error is required when status is FAILURE");
            } else {
                if (error.category() == null) {
                    problems.add("error.category is required");
                }
                requireText(problems, "error.code", error.code());
            }
        } else if (event.error() != null) {
            problems.add("error must be absent unless status is FAILURE");
        }

        if (event.channel() == null) {
            problems.add("channel is required");
        }
        if (event.traceId() == null || !TRACE_ID.matcher(event.traceId()).matches()
                || event.traceId().chars().allMatch(c -> c == '0')) {
            problems.add("traceId is required, 32 lowercase hex");
        }
        if (event.occurredAt() == null) {
            problems.add("occurredAt is required");
        }
        return problems;
    }

    private static void requireText(List<String> problems, String field, String value) {
        if (value == null || value.isBlank()) {
            problems.add(field + " is required");
        }
    }
}
