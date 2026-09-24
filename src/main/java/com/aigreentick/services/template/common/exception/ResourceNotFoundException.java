package com.aigreentick.services.template.common.exception;

import com.aigreentick.services.template.common.error.ErrorCode;

/**
 * The requested resource does not exist, or is not visible to the calling
 * project. HTTP 404.
 */
public class ResourceNotFoundException extends BaseApplicationException {

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String resourceName, String fieldName, Object fieldValue) {
        super(errorCode, String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
