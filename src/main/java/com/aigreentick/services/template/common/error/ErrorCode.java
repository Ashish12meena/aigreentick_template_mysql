package com.aigreentick.services.template.common.error;

import org.springframework.http.HttpStatus;

/**
 * Every application-level result code this API can return in the
 * {@code code} field of the response wrapper (API Standard §6).
 *
 * <h2>Why the HTTP status lives on the code</h2>
 *
 * The standard requires the body's {@code status} to always equal the HTTP
 * status, and each code to use "the HTTP status that fits". Binding the two
 * here means an exception only has to name its code; nothing downstream can
 * pair {@code TEMPLATE_NOT_FOUND} with a {@code 500} by mistake, and
 * {@code GlobalExceptionHandler} needs no per-exception status table.
 *
 * <h2>Generic vs. resource codes</h2>
 *
 * The first block is the standard's shared vocabulary; the frontend handles
 * those generically by HTTP status. The second block follows the standard's
 * {@code <RESOURCE>_<PROBLEM>} form for cases a client may want to handle
 * specifically. Add a code only when something throws it.
 */
public enum ErrorCode {

    // -- Standard codes (API Standard §6) ------------------------------
    /** Body or header can't be read, or a required header is missing/invalid. */
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    /** Caller is not authenticated (internal API key missing or wrong). */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    /** Resource or endpoint does not exist. */
    NOT_FOUND(HttpStatus.NOT_FOUND),
    /** Duplicate, or clashes with the current state. */
    CONFLICT(HttpStatus.CONFLICT),
    /** Invalid field values; details in {@code errors}. */
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    /** Unexpected error. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    /** Another service or provider failed. */
    DEPENDENCY_FAILURE(HttpStatus.BAD_GATEWAY),
    /** Another service or provider took too long. */
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),

    // -- Protocol-level codes the standard does not list ----------------
    /** HTTP method not supported on this path. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    /** Uploaded file exceeds the configured multipart limit. */
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    /** Content-Type not accepted by this endpoint. */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // -- Resource-specific codes (<RESOURCE>_<PROBLEM>) ----------------
    /** The template does not exist in the calling project. */
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** A template with the same name and language already exists on this WABA. */
    TEMPLATE_ALREADY_EXISTS(HttpStatus.CONFLICT),
    /** The template's current status does not allow this operation (e.g. not a draft). */
    TEMPLATE_INVALID_STATE(HttpStatus.CONFLICT),
    /** The library template does not exist (or is inactive, for public reads). */
    SYSTEM_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** A library template with the same name and language already exists. */
    SYSTEM_TEMPLATE_ALREADY_EXISTS(HttpStatus.CONFLICT),
    /** waba-service could not supply a usable Meta access token. */
    WABA_CREDENTIALS_UNAVAILABLE(HttpStatus.BAD_GATEWAY),
    /** Meta or storage-service failed the media upload. */
    MEDIA_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY),
    /** A create/send request arrived without a usable X-Idempotency-Key. Same code as storage-service. */
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST),
    /** A request with this idempotency key is still being processed. */
    IDEMPOTENCY_KEY_IN_PROGRESS(HttpStatus.CONFLICT),
    /** This idempotency key was already used for a different request. */
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    /**
     * Generic code for a bare HTTP status, used where only a status is known
     * (the servlet {@code /error} fallback).
     */
    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHENTICATED;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 422 -> VALIDATION_FAILED;
            case 502 -> DEPENDENCY_FAILURE;
            case 504 -> TIMEOUT;
            default -> status >= 400 && status < 500 ? BAD_REQUEST : INTERNAL_ERROR;
        };
    }
}
