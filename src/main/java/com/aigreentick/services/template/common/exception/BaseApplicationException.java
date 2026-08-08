package com.aigreentick.services.template.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Base exception for all application-level errors.
 * Carries an HTTP status so the global handler can map it directly.
 */
@Getter
public abstract class BaseApplicationException extends RuntimeException {

    private final HttpStatus httpStatus;

    protected BaseApplicationException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    protected BaseApplicationException(String message, Throwable cause, HttpStatus httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }
}