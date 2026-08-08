package com.aigreentick.services.template.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an external service (Facebook API, Media Service) fails.
 * Maps to HTTP 502 Bad Gateway.
 */
public class ExternalServiceException extends BaseApplicationException {

    public ExternalServiceException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_GATEWAY);
    }
}