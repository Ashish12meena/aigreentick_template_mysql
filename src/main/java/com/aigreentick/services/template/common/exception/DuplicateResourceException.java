package com.aigreentick.services.template.common.exception;

import com.aigreentick.services.template.common.error.ErrorCode;

/**
 * A resource with the same natural key already exists. HTTP 409.
 */
public class DuplicateResourceException extends BaseApplicationException {

    public DuplicateResourceException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public DuplicateResourceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
