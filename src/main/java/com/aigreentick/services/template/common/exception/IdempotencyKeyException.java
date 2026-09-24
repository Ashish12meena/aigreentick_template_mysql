package com.aigreentick.services.template.common.exception;

import com.aigreentick.services.template.common.error.ErrorCode;

/**
 * An {@code X-Idempotency-Key} could not be honoured: missing where required,
 * malformed, still in progress, or already used for a different request.
 */
public class IdempotencyKeyException extends BaseApplicationException {

    public IdempotencyKeyException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
