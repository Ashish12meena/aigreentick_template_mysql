package com.aigreentick.services.template.api.advice;

import com.aigreentick.services.template.api.response.error.ErrorResponse;
import com.aigreentick.services.template.application.validation.Violation;
import com.aigreentick.services.template.common.constant.LogKeys;
import com.aigreentick.services.template.common.error.ErrorCode;
import com.aigreentick.services.template.common.exception.BaseApplicationException;
import com.aigreentick.services.template.common.exception.DuplicateResourceException;
import com.aigreentick.services.template.common.exception.ExternalServiceException;
import com.aigreentick.services.template.common.exception.InvalidTemplateStateException;
import com.aigreentick.services.template.common.exception.MediaUploadException;
import com.aigreentick.services.template.common.exception.ResourceNotFoundException;
import com.aigreentick.services.template.common.exception.TemplateRuleViolationException;
import com.aigreentick.services.template.common.exception.WhatsappCredentialsNotFoundException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates every exception into the one {@link ErrorResponse} envelope.
 *
 * <h2>Why this moved out of {@code common.exception}</h2>
 *
 * It builds HTTP responses and depends on the servlet API — it is a web-layer
 * component. Sitting in {@code common} forced a {@code common -> api}
 * dependency and misrepresented {@code common} as a package the API layer
 * depends on in both directions. The exception <em>types</em> stay in
 * {@code common.exception}, where any layer may throw them; only the HTTP
 * translation lives here.
 *
 * <h2>What changed beyond the move</h2>
 *
 * <ul>
 *   <li>Every response now carries an {@link ErrorCode}. Previously a caller
 *       had to match prose to tell "this template does not exist" from "no
 *       handler is mapped here" — both plain {@code 404}s.</li>
 *   <li>Every response carries the {@code traceId} set by
 *       {@code CorrelationIdFilter}, so a caller reporting a failure can quote
 *       one id instead of describing when it happened.</li>
 *   <li>{@link TemplateRuleViolationException} is handled. It was thrown by
 *       the validation layer but had no handler, so a payload that broke a
 *       Meta composition rule fell through to the catch-all and returned
 *       {@code 500} — telling the caller this service is broken when in fact
 *       their template was invalid, and discarding the per-rule detail the
 *       validators had already computed.</li>
 *   <li>Handlers are ordered most-specific-first, and the
 *       {@link BaseApplicationException} handler is split into explicit
 *       subclass handlers so each can carry its own code.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ----------------------------------------------------
    // 400 - malformed or invalid requests
    // ----------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(fe.getRejectedValue())
                        .build())
                .toList();

        log.warn("Validation failed on {} - {} field error(s)", request.getRequestURI(), fieldErrors.size());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", request, fieldErrors);
    }

    /**
     * Constraint failures on controller method parameters — headers, path
     * variables, request params (Spring Framework 6.1+).
     *
     * <p>Must exist: without it the catch-all below swallows this and returns
     * {@code 500} for what is plainly a client error.
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

        log.warn("Parameter validation failed on {} - {} error(s)", request.getRequestURI(), fieldErrors.size());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> ErrorResponse.FieldError.builder()
                        .field(v.getPropertyPath() != null ? v.getPropertyPath().toString() : "unknown")
                        .message(v.getMessage())
                        .build())
                .toList();

        log.warn("Constraint violation on {} - {} error(s)", request.getRequestURI(), fieldErrors.size());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", request, fieldErrors);
    }

    /**
     * A rejected enum value (e.g. {@code format: "PNG"}) arrives here wrapped
     * in an {@link InvalidFormatException}. Without unwrapping it the client
     * gets "Malformed request body" and no indication of which field was
     * wrong, so the unwrapping is worth the extra branch.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());

        if (ex.getCause() instanceof InvalidFormatException ife) {
            String field = ife.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));

            String message = ife.getTargetType() != null && ife.getTargetType().isEnum()
                    ? String.format("Invalid value for '%s'. Allowed values: %s",
                            field, Arrays.toString(ife.getTargetType().getEnumConstants()))
                    : String.format("Invalid value for '%s'", field);

            return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message, request, null);
        }

        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST_BODY, "Malformed request body", request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        String message = String.format("Missing required parameter: '%s'", ex.getParameterName());
        log.warn("{} on {}", message, request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MISSING_PARAMETER, message, request, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {

        String message = String.format("Missing required header: '%s'", ex.getHeaderName());
        log.warn("{} on {}", message, request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MISSING_PARAMETER, message, request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String message = String.format("Invalid value '%s' for parameter '%s'. Expected type: %s",
                ex.getValue(), ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("{} on {}", message, request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message, request, null);
    }

    // ----------------------------------------------------
    // 404 / 405 - routing
    // ----------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.info("Resource not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request, null);
    }

    /**
     * Distinct from {@link #handleNotFound}: this one means the deployment is
     * wrong, not that the caller asked for a template that is gone. Both are
     * {@code 404}, which is why {@link ErrorCode} carries the difference.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {

        log.info("No handler for {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, ErrorCode.ENDPOINT_NOT_FOUND,
                "No endpoint exists for this path", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        String message = String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod());
        log.info("{} on {}", message, request.getRequestURI());
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED, message, request, null);
    }

    // ----------------------------------------------------
    // 409 / 422 - state, uniqueness and composition rules
    // ----------------------------------------------------

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateResourceException ex, HttpServletRequest request) {

        log.warn("Duplicate resource on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE, ex.getMessage(), request, null);
    }

    /**
     * Safety net for a unique-constraint violation that slipped past the
     * application-level duplicate check — for instance two concurrent creates
     * of the same name. The DB detail is deliberately not echoed back:
     * constraint names disclose schema structure.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.error("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE,
                "A resource with the same identifier already exists", request, null);
    }

    @ExceptionHandler(InvalidTemplateStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(
            InvalidTemplateStateException ex, HttpServletRequest request) {

        log.warn("Invalid template state on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.INVALID_TEMPLATE_STATE,
                ex.getMessage(), request, null);
    }

    /**
     * The payload is well-formed and every field is individually valid, but
     * the combination breaks a Meta composition rule — hence {@code 422}.
     *
     * <p>The validators have already produced a precise, per-rule
     * {@link Violation} list. Flattening it into {@code fieldErrors} means the
     * caller can highlight the offending components instead of being handed
     * one summary sentence.
     */
    @ExceptionHandler(TemplateRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleRuleViolation(
            TemplateRuleViolationException ex, HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getViolations().stream()
                .map(v -> ErrorResponse.FieldError.builder()
                        .field(v.field())
                        .code(v.code())
                        .message(v.message())
                        .build())
                .toList();

        log.warn("Template rule violation on {} - {} rule(s)", request.getRequestURI(), fieldErrors.size());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.TEMPLATE_RULE_VIOLATION,
                ex.getMessage(), request, fieldErrors);
    }

    // ----------------------------------------------------
    // 413 - payload limits
    // ----------------------------------------------------

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        log.warn("Upload too large on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE,
                "File size exceeds the maximum allowed limit", request, null);
    }

    // ----------------------------------------------------
    // 5xx - upstream and internal
    // ----------------------------------------------------

    /**
     * A credential lookup failure surfaces as {@code 502}: it means "the
     * upstream we depend on could not authorise us", not "your request was
     * bad". Attributing it correctly is what stops an on-call engineer
     * debugging this service when waba-service is the one that is down.
     */
    @ExceptionHandler(WhatsappCredentialsNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCredentialsUnavailable(
            WhatsappCredentialsNotFoundException ex, HttpServletRequest request) {

        log.error("WABA credential unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ErrorCode.UPSTREAM_CREDENTIAL_UNAVAILABLE,
                "Could not resolve WhatsApp Business Account credentials", request, null);
    }

    @ExceptionHandler(MediaUploadException.class)
    public ResponseEntity<ErrorResponse> handleMediaUpload(
            MediaUploadException ex, HttpServletRequest request) {

        log.error("Media upload failed on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_GATEWAY, ErrorCode.UPSTREAM_MEDIA_UPLOAD_FAILED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalService(
            ExternalServiceException ex, HttpServletRequest request) {

        log.error("Upstream call failed on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_GATEWAY, ErrorCode.UPSTREAM_META_API_FAILED, ex.getMessage(), request, null);
    }

    /**
     * Any {@link BaseApplicationException} subclass added later, before
     * someone gets round to giving it a dedicated handler. It still carries
     * its own status, so the response is correct — only the
     * {@link ErrorCode} is coarse.
     */
    @ExceptionHandler(BaseApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(
            BaseApplicationException ex, HttpServletRequest request) {

        HttpStatus status = ex.getHttpStatus();
        if (status.is5xxServerError()) {
            log.error("Application error [{}] on {}: {}", status.value(), request.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.warn("Application error [{}] on {}: {}", status.value(), request.getRequestURI(), ex.getMessage());
        }

        ErrorCode code = status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.INVALID_REQUEST;
        return build(status, code, ex.getMessage(), request, null);
    }

    /**
     * Safety net. Logs the full stack trace but returns a generic message:
     * an exception message can contain a SQL fragment, a file path or a
     * token, and none of those belong in a response body.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Please try again later.", request, null);
    }

    // ----------------------------------------------------
    // Helper
    // ----------------------------------------------------

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, ErrorCode errorCode, String message,
            HttpServletRequest request, List<ErrorResponse.FieldError> fieldErrors) {

        ErrorResponse body = ErrorResponse.builder()
                .status("ERROR")
                .code(status.value())
                .errorCode(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                // Set by CorrelationIdFilter; lets a caller quote one id when
                // reporting a failure instead of describing when it happened.
                .traceId(MDC.get(LogKeys.TRACE_ID))
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }
}
