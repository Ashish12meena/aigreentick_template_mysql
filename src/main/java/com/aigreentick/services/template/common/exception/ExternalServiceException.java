package com.aigreentick.services.template.common.exception;

import com.aigreentick.services.template.common.error.ErrorCode;

/**
 * An upstream (Meta Graph API, waba-service, storage-service) failed.
 * HTTP 502 {@code DEPENDENCY_FAILURE}; the exception handler upgrades it to
 * 504 {@code TIMEOUT} when the cause is a timeout.
 */
public class ExternalServiceException extends BaseApplicationException {

    public ExternalServiceException(String message) {
        super(ErrorCode.DEPENDENCY_FAILURE, message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(ErrorCode.DEPENDENCY_FAILURE, message, cause);
    }
}
