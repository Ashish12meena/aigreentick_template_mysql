package com.aigreentick.services.template.api.response.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/**
 * The only way controllers build a response, one method per row of the
 * status-code table in API Standard §4. Keeping the HTTP status and the
 * wrapper's {@code status} in one call is what guarantees they match.
 */
public final class Responses {

    private Responses() {
    }

    /** 200: read, update with result, action with result, delete with summary. */
    public static <T> ResponseEntity<ApiEnvelope<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK, message, data));
    }

    /** 201 + {@code Location}: resource created. */
    public static <T> ResponseEntity<ApiEnvelope<T>> created(URI location, String message, T data) {
        return ResponseEntity.created(location).body(ApiEnvelope.success(HttpStatus.CREATED, message, data));
    }

    /** 202: accepted for background processing; data is {@code {jobId, statusUrl}}. */
    public static <T> ResponseEntity<ApiEnvelope<T>> accepted(String message, T data) {
        return ResponseEntity.accepted().body(ApiEnvelope.success(HttpStatus.ACCEPTED, message, data));
    }

    /** 204: done, nothing to return. No body, so no wrapper; X-Request-Id is still sent. */
    public static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }
}
