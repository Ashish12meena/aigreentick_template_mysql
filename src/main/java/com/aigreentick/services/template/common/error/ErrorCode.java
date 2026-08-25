package com.aigreentick.services.template.common.error;

/**
 * Stable, machine-readable classification for every error this API returns.
 *
 * <h2>Why this exists</h2>
 *
 * Before this, a caller could not distinguish "this template name is already
 * taken" from "Meta rejected the submission" without matching English prose:
 * both surfaced with only the {@code message} field differing. Message text
 * is written for humans and is free to change; a code is a contract.
 *
 * <p>The Messaging Service is the case that matters. It calls
 * {@code GET /api/v1/templates/{templateId}} on the send path and must be
 * able to tell "this template does not exist, stop retrying" from "the
 * template store is briefly unavailable, retry" — a distinction that is not
 * safely recoverable from a sentence.
 *
 * <p>The code is additive: every field the error envelope had before is
 * still present, so existing consumers are unaffected.
 *
 * <p>Values are namespaced by concern rather than numbered, so a code
 * appearing in a log or a support ticket is readable without a lookup table.
 */
public enum ErrorCode {

    // -- Client errors -------------------------------------------------
    /** Bean-validation or constraint failure on the request. */
    VALIDATION_FAILED,
    /** Request was well-formed but semantically invalid for this endpoint. */
    INVALID_REQUEST,
    /** Request body could not be parsed. */
    MALFORMED_REQUEST_BODY,
    /** A required query parameter, path variable or header was absent. */
    MISSING_PARAMETER,
    /** The requested template does not exist, or is not visible to this tenant. */
    RESOURCE_NOT_FOUND,
    /** No handler is mapped to the requested path. */
    ENDPOINT_NOT_FOUND,
    /** The HTTP method is not supported for this path. */
    METHOD_NOT_ALLOWED,
    /** Caller failed internal API authentication. */
    UNAUTHORIZED,
    /** Uploaded file exceeded the configured multipart limit. */
    PAYLOAD_TOO_LARGE,

    // -- Conflicts / state ---------------------------------------------
    /** A template with the same name and language already exists on this WABA. */
    DUPLICATE_RESOURCE,
    /** The template is not in a state that permits this transition. */
    INVALID_TEMPLATE_STATE,
    /** The payload is individually valid but breaks a Meta composition rule. */
    TEMPLATE_RULE_VIOLATION,

    // -- Upstream / server ---------------------------------------------
    /** The Meta Graph API call failed. */
    UPSTREAM_META_API_FAILED,
    /** waba-service could not supply a usable credential. */
    UPSTREAM_CREDENTIAL_UNAVAILABLE,
    /** storage-service rejected or failed the media upload. */
    UPSTREAM_MEDIA_UPLOAD_FAILED,
    /** Anything not otherwise classified. */
    INTERNAL_ERROR
}
