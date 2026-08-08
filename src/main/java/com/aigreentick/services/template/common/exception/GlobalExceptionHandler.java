package com.aigreentick.services.template.common.exception;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.aigreentick.services.template.api.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralized exception handler for the entire Template Service.
 *
 * Mapping strategy:
 * - {@link BaseApplicationException} subclasses → status from exception itself
 * - Spring validation / binding errors → 400
 * - Missing headers / params → 400
 * - DB constraint violations → 409
 * - Everything else → 500 (sanitized)
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        // ────────────────────────────────────────────────
        // 1. Custom application exceptions
        // ────────────────────────────────────────────────

        /**
         * Handles all exceptions that extend {@link BaseApplicationException}.
         * This covers ResourceNotFoundException (404), DuplicateResourceException
         * (409),
         * InvalidTemplateStateException (422), ExternalServiceException (502),
         * MediaUploadException (502), WhatsappCredentialsNotFoundException (502).
         */
        @ExceptionHandler(BaseApplicationException.class)
        public ResponseEntity<ErrorResponse> handleApplicationException(
                        BaseApplicationException ex, HttpServletRequest request) {

                HttpStatus status = ex.getHttpStatus();

                // Log at appropriate level based on status
                if (status.is5xxServerError()) {
                        log.error("Application error [{}]: {} — path: {}",
                                        status.value(), ex.getMessage(), request.getRequestURI(), ex);
                } else {
                        log.warn("Application error [{}]: {} — path: {}",
                                        status.value(), ex.getMessage(), request.getRequestURI());
                }

                return buildResponse(status, ex.getMessage(), request);
        }

        // ────────────────────────────────────────────────
        // 2. Validation & binding errors → 400
        // ────────────────────────────────────────────────

        /**
         * Handles @Valid / @Validated failures on request bodies.
         * Returns per-field error details.
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidationException(
                        MethodArgumentNotValidException ex, HttpServletRequest request) {

                List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(fe -> ErrorResponse.FieldError.builder()
                                                .field(fe.getField())
                                                .message(fe.getDefaultMessage())
                                                .rejectedValue(fe.getRejectedValue())
                                                .build())
                                .toList();

                log.warn("Validation failed on {} — {} field error(s)",
                                request.getRequestURI(), fieldErrors.size());

                ErrorResponse body = ErrorResponse.builder()
                                .status("ERROR")
                                .code(HttpStatus.BAD_REQUEST.value())
                                .message("Validation failed")
                                .path(request.getRequestURI())
                                .timestamp(Instant.now())
                                .fieldErrors(fieldErrors)
                                .build();

                return ResponseEntity.badRequest().body(body);
        }

        /**
         * Handles malformed JSON or unreadable request bodies.
         */
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ErrorResponse> handleUnreadableBody(
                        HttpMessageNotReadableException ex, HttpServletRequest request) {

                log.warn("Malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());

                // A rejected enum value (e.g. format: "PNG") arrives here as an
                // InvalidFormatException. Without unwrapping it the client just gets
                // "Malformed request body" and has no idea which field was wrong.
                Throwable cause = ex.getCause();
                if (cause instanceof InvalidFormatException ife) {
                        String field = ife.getPath().stream()
                                        .map(ref -> ref.getFieldName() != null
                                                        ? ref.getFieldName()
                                                        : "[" + ref.getIndex() + "]")
                                        .collect(Collectors.joining("."));

                        String message = ife.getTargetType() != null && ife.getTargetType().isEnum()
                                        ? String.format("Invalid value for '%s'. Allowed values: %s",
                                                        field, Arrays.toString(ife.getTargetType().getEnumConstants()))
                                        : String.format("Invalid value for '%s'", field);

                        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
                }

                return buildResponse(HttpStatus.BAD_REQUEST,
                                "Malformed request body", request);
        }

        /**
         * Handles missing required request parameters.
         */
        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<ErrorResponse> handleMissingParam(
                        MissingServletRequestParameterException ex, HttpServletRequest request) {

                String msg = String.format("Missing required parameter: '%s'", ex.getParameterName());
                log.warn("{} on {}", msg, request.getRequestURI());
                return buildResponse(HttpStatus.BAD_REQUEST, msg, request);
        }

        /**
         * Handles missing required headers (X-Project-Id, X-Organization-Id, etc.).
         */
        @ExceptionHandler(MissingRequestHeaderException.class)
        public ResponseEntity<ErrorResponse> handleMissingHeader(
                        MissingRequestHeaderException ex, HttpServletRequest request) {

                String msg = String.format("Missing required header: '%s'", ex.getHeaderName());
                log.warn("{} on {}", msg, request.getRequestURI());
                return buildResponse(HttpStatus.BAD_REQUEST, msg, request);
        }

        /**
         * Handles type conversion failures (e.g., "abc" passed for a Long path
         * variable).
         */
        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ErrorResponse> handleTypeMismatch(
                        MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

                String msg = String.format("Invalid value '%s' for parameter '%s'. Expected type: %s",
                                ex.getValue(), ex.getName(),
                                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
                log.warn("{} on {}", msg, request.getRequestURI());
                return buildResponse(HttpStatus.BAD_REQUEST, msg, request);
        }

        // ────────────────────────────────────────────────
        // 3. Data integrity → 409
        // ────────────────────────────────────────────────

        /**
         * Handles DB unique constraint violations that slip past application-level
         * checks.
         * This is a safety net — ideally caught earlier by ensureNoDuplicate().
         */
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ErrorResponse> handleDataIntegrity(
                        DataIntegrityViolationException ex, HttpServletRequest request) {

                log.error("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMessage());

                // Don't leak DB details — return a generic message
                return buildResponse(HttpStatus.CONFLICT,
                                "A resource with the same identifier already exists", request);
        }

        // ────────────────────────────────────────────────
        // 4. Method not allowed → 405
        // ────────────────────────────────────────────────

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ErrorResponse> handleMethodNotSupported(
                        HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

                String msg = String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod());
                log.warn("{} on {}", msg, request.getRequestURI());
                return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, msg, request);
        }

        // ────────────────────────────────────────────────
        // 5. Resource not found (Spring-level) → 404
        // ────────────────────────────────────────────────

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ErrorResponse> handleNoResourceFound(
                        NoResourceFoundException ex, HttpServletRequest request) {

                return buildResponse(HttpStatus.NOT_FOUND,
                                "Endpoint not found: " + request.getRequestURI(), request);
        }

        // ────────────────────────────────────────────────
        // 6. File upload too large → 413
        // ────────────────────────────────────────────────

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ErrorResponse> handleMaxUploadSize(
                        MaxUploadSizeExceededException ex, HttpServletRequest request) {

                log.warn("Upload too large on {}: {}", request.getRequestURI(), ex.getMessage());
                return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE,
                                "File size exceeds the maximum allowed limit", request);
        }

        // ────────────────────────────────────────────────
        // 7. Catch-all → 500 (sanitized)
        // ────────────────────────────────────────────────

        /**
         * Safety net for anything not caught above.
         * Logs the full stack trace but returns a generic message to the client.
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleUnexpected(
                        Exception ex, HttpServletRequest request) {

                log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

                return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                                "An unexpected error occurred. Please try again later.", request);
        }

        // ────────────────────────────────────────────────
        // Helper
        // ────────────────────────────────────────────────

        private ResponseEntity<ErrorResponse> buildResponse(
                        HttpStatus status, String message, HttpServletRequest request) {

                ErrorResponse body = ErrorResponse.builder()
                                .status("ERROR")
                                .code(status.value())
                                .message(message)
                                .path(request.getRequestURI())
                                .timestamp(Instant.now())
                                .build();

                return ResponseEntity.status(status).body(body);
        }

        /**
         * Handles constraint failures on controller method parameters — headers,
         * path variables, request params (Spring Framework 6.1+).
         *
         * MUST exist: without it the catch-all Exception handler below swallows
         * this and returns 500 for what is plainly a client error.
         */
        @ExceptionHandler(HandlerMethodValidationException.class)
        public ResponseEntity<ErrorResponse> handleMethodValidation(
                        HandlerMethodValidationException ex, HttpServletRequest request) {

                List<ErrorResponse.FieldError> fieldErrors = ex.getAllValidationResults().stream()
                                .flatMap(result -> {
                                        String name = result.getMethodParameter().getParameterName();
                                        return result.getResolvableErrors().stream()
                                                        .map(err -> ErrorResponse.FieldError.builder()
                                                                        .field(name != null ? name : "parameter")
                                                                        .message(err.getDefaultMessage())
                                                                        .build());
                                })
                                .toList();

                log.warn("Parameter validation failed on {} — {} error(s)",
                                request.getRequestURI(), fieldErrors.size());

                ErrorResponse body = ErrorResponse.builder()
                                .status("ERROR")
                                .code(HttpStatus.BAD_REQUEST.value())
                                .message("Validation failed")
                                .path(request.getRequestURI())
                                .timestamp(Instant.now())
                                .fieldErrors(fieldErrors)
                                .build();

                return ResponseEntity.badRequest().body(body);
        }

        /**
         * Handles @Validated constraint violations raised outside the controller
         * layer (service methods, custom validators). Finding EXC-1.
         */
        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ErrorResponse> handleConstraintViolation(
                        ConstraintViolationException ex, HttpServletRequest request) {

                List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                                .map(v -> ErrorResponse.FieldError.builder()
                                                .field(v.getPropertyPath() != null ? v.getPropertyPath().toString()
                                                                : "unknown")
                                                .message(v.getMessage())
                                                .build())
                                .toList();

                log.warn("Constraint violation on {} — {} error(s)",
                                request.getRequestURI(), fieldErrors.size());

                ErrorResponse body = ErrorResponse.builder()
                                .status("ERROR")
                                .code(HttpStatus.BAD_REQUEST.value())
                                .message("Validation failed")
                                .path(request.getRequestURI())
                                .timestamp(Instant.now())
                                .fieldErrors(fieldErrors)
                                .build();

                return ResponseEntity.badRequest().body(body);
        }
}