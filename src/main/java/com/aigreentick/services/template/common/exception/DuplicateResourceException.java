package com.aigreentick.services.template.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when attempting to create a resource that already exists.
 * Maps to HTTP 409 Conflict.
 */
public class DuplicateResourceException extends BaseApplicationException {

    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}