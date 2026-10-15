package com.apargo.services.template.infrastructure.kafka;

/**
 * A template webhook record that no retry can fix (it does not parse). The
 * error handler skips it without backing off.
 */
public class NonRetryableTemplateEventException extends RuntimeException {

    public NonRetryableTemplateEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
