package com.aigreentick.services.template.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation is attempted on a resource in an invalid state.
 * E.g., submitting a non-DRAFT template, updating an approved template.
 * Maps to HTTP 422 Unprocessable Entity.
 */
public class InvalidTemplateStateException extends BaseApplicationException {

    public InvalidTemplateStateException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}