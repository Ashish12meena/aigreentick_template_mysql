package com.aigreentick.services.template.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when media upload to Facebook or internal storage fails.
 * Maps to HTTP 502 Bad Gateway.
 */
public class MediaUploadException extends BaseApplicationException {

    public MediaUploadException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public MediaUploadException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_GATEWAY);  // FIX: cause was being swallowed before
    }
}