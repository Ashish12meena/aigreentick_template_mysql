package com.aigreentick.services.template.common.exception;

import com.aigreentick.services.template.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Base type for every error this service raises deliberately.
 *
 * <p>It carries an {@link ErrorCode}, and the code carries the HTTP status,
 * so {@code GlobalExceptionHandler} can render any subclass with one handler
 * and the {@code status}/{@code code} pair in the response is always
 * consistent (API Standard §4, §6).
 *
 * <p>{@link #getMessage()} is shown to the caller: never put SQL, stack
 * traces, tokens or URLs with credentials in it.
 */
@Getter
public abstract class BaseApplicationException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BaseApplicationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BaseApplicationException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public HttpStatus getHttpStatus() {
        return errorCode.httpStatus();
    }
}
