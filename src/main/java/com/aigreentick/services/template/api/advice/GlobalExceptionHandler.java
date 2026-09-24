package com.aigreentick.services.template.api.advice;

import com.aigreentick.services.template.api.response.common.ApiEnvelope;
import com.aigreentick.services.template.api.response.common.ApiFieldError;
import com.aigreentick.services.template.common.error.ErrorCode;
import com.aigreentick.services.template.common.error.FieldErrorCode;
import com.aigreentick.services.template.common.exception.BaseApplicationException;
import com.aigreentick.services.template.common.exception.TemplateRuleViolationException;
import com.aigreentick.services.template.common.exception.WhatsappCredentialsNotFoundException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Translates every exception into the standard error wrapper (API Standard §6).
 *
 * <h2>How statuses are chosen</h2>
 * <ul>
 *   <li><b>400 {@code BAD_REQUEST}</b> — the body or a header can't be read:
 *       malformed JSON, a missing or invalid tenancy header.</li>
 *   <li><b>422 {@code VALIDATION_FAILED}</b> — the request was read but field
 *       values are invalid: bean validation on the body, query parameters and
 *       path variables, and Meta composition rules. Details go in
 *       {@code errors[]} with standard field codes.</li>
 *   <li><b>Application errors</b> — every {@link BaseApplicationException}
 *       carries an {@link ErrorCode}, and the code carries its HTTP status,
 *       so one handler renders all of them.</li>
 *   <li><b>Upstream timeouts</b> — reported as 504 {@code TIMEOUT} instead
 *       of 502, so the client knows a retry is reasonable.</li>
 * </ul>
 *
 * <p>{@code message} never contains stack traces, SQL or secrets: unexpected
 * exceptions are logged in full and answered with a generic text.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_MESSAGE = "Request has invalid fields";

    // ----------------------------------------------------
    // 422 - field validation
    // ----------------------------------------------------

    /** {@code @Valid @RequestBody} failures when method validation does not apply. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleBodyValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ApiFieldError> errors = toFieldErrors(ex.getBindingResult());
        log.warn("Body validation failed on {} - {} field error(s)", request.getRequestURI(), errors.size());
        return validationFailed(errors, request);
    }

    /**
     * Constraint failures found by Spring's built-in method validation: on
     * headers, path variables, query parameters and (when parameter
     * constraints are present) the {@code @Valid} body too.
     *
     * <p>A bad <em>header</em> means the request can't be used at all, so any
     * header failure makes the whole response 400 {@code BAD_REQUEST}.
     * Otherwise it is 422 with one {@code errors[]} entry per problem.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {

        List<String> headerProblems = new ArrayList<>();
        List<ApiFieldError> errors = new ArrayList<>();

        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {
                errors.addAll(toFieldErrors(bodyErrors));
                continue;
            }
            MethodParameter parameter = result.getMethodParameter();
            RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                if (header != null) {
                    headerProblems.add(String.format("'%s' %s", headerName(header, parameter), error.getDefaultMessage()));
                } else {
                    errors.add(new ApiFieldError(requestName(parameter),
                            FieldErrorCode.fromCodes(error.getCodes()).name(), error.getDefaultMessage()));
                }
            }
        }

        if (!headerProblems.isEmpty()) {
            String message = "Invalid header " + String.join("; ", headerProblems);
            log.warn("{} on {}", message, request.getRequestURI());
            return error(ErrorCode.BAD_REQUEST, message, request);
        }

        log.warn("Parameter validation failed on {} - {} error(s)", request.getRequestURI(), errors.size());
        return validationFailed(errors, request);
    }

    /** Validation raised by {@code @Validated} beans outside the MVC argument pipeline. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ApiFieldError(leafName(v), constraintCode(v), v.getMessage()))
                .toList();

        log.warn("Constraint violation on {} - {} error(s)", request.getRequestURI(), errors.size());
        return validationFailed(errors, request);
    }

    /**
     * Meta composition rules. Each violation keeps its dotted field path and
     * its stable {@code META_*} code, so the client can highlight the exact
     * component.
     */
    @ExceptionHandler(TemplateRuleViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleRuleViolation(
            TemplateRuleViolationException ex, HttpServletRequest request) {

        List<ApiFieldError> errors = ex.getViolations().stream()
                .map(v -> new ApiFieldError(v.field(), v.code(), v.message()))
                .toList();

        log.warn("Template rule violation on {} - {} rule(s)", request.getRequestURI(), errors.size());
        return error(ErrorCode.VALIDATION_FAILED, ex.getMessage(), errors, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        log.warn("Missing parameter '{}' on {}", ex.getParameterName(), request.getRequestURI());
        return validationFailed(List.of(new ApiFieldError(ex.getParameterName(),
                FieldErrorCode.REQUIRED.name(), ex.getParameterName() + " is required")), request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {

        log.warn("Missing multipart part '{}' on {}", ex.getRequestPartName(), request.getRequestURI());
        return validationFailed(List.of(new ApiFieldError(ex.getRequestPartName(),
                FieldErrorCode.REQUIRED.name(), ex.getRequestPartName() + " is required")), request);
    }

    /**
     * A value that can't be converted to the parameter type, e.g.
     * {@code ?status=FOO} or {@code X-Project-Id: abc}. Headers are 400 (the
     * request can't be used); query and path values are 422 field errors.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        MethodParameter parameter = ex.getParameter();
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        Class<?> type = ex.getRequiredType();

        if (header != null) {
            String message = String.format("Invalid header '%s': expected %s",
                    headerName(header, parameter), type != null ? type.getSimpleName() : "a different type");
            log.warn("{} on {}", message, request.getRequestURI());
            return error(ErrorCode.BAD_REQUEST, message, request);
        }

        String name = requestName(parameter);
        String message = type != null && type.isEnum()
                ? String.format("%s must be one of %s", name, Arrays.toString(type.getEnumConstants()))
                : String.format("%s has an invalid value", name);
        log.warn("{} on {}", message, request.getRequestURI());
        return validationFailed(List.of(new ApiFieldError(name, FieldErrorCode.INVALID_VALUE.name(), message)), request);
    }

    // ----------------------------------------------------
    // 400 - unreadable request
    // ----------------------------------------------------

    /**
     * Unparseable JSON is 400. A well-formed body holding a value of the
     * wrong type for a field (e.g. an unknown enum constant) is a field
     * error: 422 naming that field.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Unreadable request body on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());

        if (ex.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            String field = jsonPath(mie.getPath());
            Class<?> target = mie.getTargetType();
            String message = mie instanceof InvalidFormatException && target != null && target.isEnum()
                    ? String.format("%s must be one of %s", field, Arrays.toString(target.getEnumConstants()))
                    : String.format("%s has an invalid value", field);
            return validationFailed(List.of(new ApiFieldError(field, FieldErrorCode.INVALID_VALUE.name(), message)), request);
        }

        return error(ErrorCode.BAD_REQUEST, "Request body is missing or is not valid JSON", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {

        String message = String.format("Missing required header '%s'", ex.getHeaderName());
        log.warn("{} on {}", message, request.getRequestURI());
        return error(ErrorCode.BAD_REQUEST, message, request);
    }

    // ----------------------------------------------------
    // Protocol-level: 404 / 405 / 413 / 415
    // ----------------------------------------------------

    /** No handler mapped to this path: the deployment or the URL is wrong, not the data. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {

        log.info("No handler for {} {}", request.getMethod(), request.getRequestURI());
        return error(ErrorCode.NOT_FOUND, "No endpoint exists for this path", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        String message = String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod());
        log.info("{} on {}", message, request.getRequestURI());
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.httpStatus()).headers(headers)
                .body(envelope(ErrorCode.METHOD_NOT_ALLOWED, message, List.of(), request));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        log.warn("Upload too large on {}: {}", request.getRequestURI(), ex.getMessage());
        return error(ErrorCode.PAYLOAD_TOO_LARGE, "File size exceeds the maximum allowed limit", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        String message = String.format("Content-Type '%s' is not supported; use %s",
                ex.getContentType(), ex.getSupportedMediaTypes());
        log.warn("{} on {}", message, request.getRequestURI());
        return error(ErrorCode.UNSUPPORTED_MEDIA_TYPE, message, request);
    }

    // ----------------------------------------------------
    // 409 - conflicts not raised as application exceptions
    // ----------------------------------------------------

    /**
     * Safety net for a unique-constraint violation that slipped past the
     * application-level duplicate check (e.g. two concurrent creates). The DB
     * detail is not echoed: constraint names disclose schema structure.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.error("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return error(ErrorCode.CONFLICT, "A resource with the same identifier already exists", request);
    }

    // ----------------------------------------------------
    // Application exceptions (4xx and 5xx)
    // ----------------------------------------------------

    /**
     * The upstream message can echo waba-service's response body, so the
     * caller gets a fixed text; the detail is in the log.
     */
    @ExceptionHandler(WhatsappCredentialsNotFoundException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleCredentialsUnavailable(
            WhatsappCredentialsNotFoundException ex, HttpServletRequest request) {

        log.error("WABA credentials unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
        return error(timeoutOr(ex.getErrorCode(), ex),
                "Could not resolve WhatsApp Business Account credentials", request);
    }

    /** Every other deliberate error: the exception's {@link ErrorCode} decides the status. */
    @ExceptionHandler(BaseApplicationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleApplicationException(
            BaseApplicationException ex, HttpServletRequest request) {

        ErrorCode code = timeoutOr(ex.getErrorCode(), ex);
        if (code.httpStatus().is5xxServerError()) {
            log.error("{} on {}: {}", code, request.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.warn("{} on {}: {}", code, request.getRequestURI(), ex.getMessage());
        }
        return error(code, ex.getMessage(), request);
    }

    /** An outbound call failed without an adapter translating it. */
    @ExceptionHandler(WebClientException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleUpstream(WebClientException ex, HttpServletRequest request) {

        ErrorCode code = timeoutOr(ErrorCode.DEPENDENCY_FAILURE, ex);
        log.error("Untranslated upstream failure on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return error(code, code == ErrorCode.TIMEOUT
                ? "A dependent service did not respond in time"
                : "A dependent service failed", request);
    }

    /**
     * Safety net. Any remaining Spring MVC exception already knows its status
     * ({@link ErrorResponse}); everything else is a 500 with a generic
     * message, because an exception message can hold SQL, a path or a token.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {

        if (ex instanceof ErrorResponse springError && springError.getStatusCode().is4xxClientError()) {
            HttpStatusCode reported = springError.getStatusCode();
            log.warn("{} on {}: {}", reported, request.getRequestURI(), ex.getMessage());
            ErrorCode code = ErrorCode.forStatus(reported.value());
            HttpStatus resolved = HttpStatus.resolve(reported.value());
            // One HttpStatus for both the response line and the body, so they can't disagree.
            HttpStatus status = resolved != null ? resolved : code.httpStatus();
            return ResponseEntity.status(status).body(ApiEnvelope.error(
                    status, code.name(), status.getReasonPhrase(), List.of(), request.getRequestURI()));
        }

        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return error(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred. Please try again later.", request);
    }

    // ----------------------------------------------------
    // Helpers
    // ----------------------------------------------------

    private ResponseEntity<ApiEnvelope<Void>> validationFailed(List<ApiFieldError> errors, HttpServletRequest request) {
        return error(ErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE, errors, request);
    }

    private ResponseEntity<ApiEnvelope<Void>> error(ErrorCode code, String message, HttpServletRequest request) {
        return error(code, message, List.of(), request);
    }

    private ResponseEntity<ApiEnvelope<Void>> error(ErrorCode code, String message,
                                                    List<ApiFieldError> errors, HttpServletRequest request) {
        return ResponseEntity.status(code.httpStatus()).body(envelope(code, message, errors, request));
    }

    private ApiEnvelope<Void> envelope(ErrorCode code, String message,
                                       List<ApiFieldError> errors, HttpServletRequest request) {
        return ApiEnvelope.error(code.httpStatus(), code.name(), message, errors, request.getRequestURI());
    }

    private List<ApiFieldError> toFieldErrors(Errors errors) {
        List<ApiFieldError> result = new ArrayList<>();
        errors.getFieldErrors().forEach(fe -> result.add(new ApiFieldError(
                fe.getField(), FieldErrorCode.fromConstraint(fe.getCode()).name(), fe.getDefaultMessage())));
        errors.getGlobalErrors().forEach(ge -> result.add(new ApiFieldError(
                ge.getObjectName(), FieldErrorCode.fromConstraint(ge.getCode()).name(), ge.getDefaultMessage())));
        return result;
    }

    /** Upgrades an upstream failure to {@code TIMEOUT} when any cause is a timeout. */
    private ErrorCode timeoutOr(ErrorCode code, Throwable ex) {
        if (code.httpStatus().value() != HttpStatus.BAD_GATEWAY.value()) {
            return code;
        }
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            // Matched by name so the web layer needs no dependency on Netty's
            // ReadTimeoutException / ConnectTimeoutException types.
            if (t instanceof java.util.concurrent.TimeoutException
                    || t instanceof java.net.SocketTimeoutException
                    || t.getClass().getSimpleName().endsWith("TimeoutException")) {
                return ErrorCode.TIMEOUT;
            }
        }
        return code;
    }

    /** Name the client used: {@code @RequestParam("x")} / {@code @PathVariable("x")} value, else the Java name. */
    private String requestName(MethodParameter parameter) {
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null) {
            String declared = firstNonBlank(param.name(), param.value());
            if (declared != null) {
                return declared;
            }
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null) {
            String declared = firstNonBlank(path.name(), path.value());
            if (declared != null) {
                return declared;
            }
        }
        String name = parameter.getParameterName();
        return name != null ? name : "parameter";
    }

    private String headerName(RequestHeader header, MethodParameter parameter) {
        String declared = firstNonBlank(header.name(), header.value());
        return declared != null ? declared : String.valueOf(parameter.getParameterName());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }

    /** {@code template.components[0].type} from Jackson's reference path. */
    private String jsonPath(List<JsonMappingException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : path) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getFieldName());
            } else {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    private String leafName(ConstraintViolation<?> violation) {
        String leaf = null;
        for (Path.Node node : violation.getPropertyPath()) {
            leaf = node.getName();
        }
        return leaf != null ? leaf : "parameter";
    }

    private String constraintCode(ConstraintViolation<?> violation) {
        return FieldErrorCode.fromConstraint(violation.getConstraintDescriptor()
                .getAnnotation().annotationType().getSimpleName()).name();
    }
}
