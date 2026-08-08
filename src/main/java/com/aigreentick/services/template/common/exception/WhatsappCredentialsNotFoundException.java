package com.aigreentick.services.template.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when WABA credentials (access token) cannot be resolved.
 * Maps to HTTP 502 Bad Gateway (upstream dependency failure).
 */
public class WhatsappCredentialsNotFoundException extends BaseApplicationException {

    public WhatsappCredentialsNotFoundException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}