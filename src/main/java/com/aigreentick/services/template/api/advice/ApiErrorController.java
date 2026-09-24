package com.aigreentick.services.template.api.advice;

import com.aigreentick.services.template.api.response.common.ApiEnvelope;
import com.aigreentick.services.template.common.constant.ApiPaths;
import com.aigreentick.services.template.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Replaces Spring Boot's default {@code /error} body.
 *
 * <p>{@link GlobalExceptionHandler} only sees exceptions thrown inside the
 * dispatcher. Anything that fails before it — a servlet filter throwing, the
 * container rejecting a request — is forwarded to {@code /error}, and Boot's
 * default answer there is a different JSON shape ({@code timestamp},
 * {@code error}, {@code path}). This keeps even those responses in the
 * standard wrapper, so the frontend really can handle every API with one
 * function.
 */
@Slf4j
@Hidden
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping(ApiPaths.ERROR)
    public ResponseEntity<ApiEnvelope<Void>> error(HttpServletRequest request) {
        Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusValue = statusAttr instanceof Integer i ? i : HttpStatus.INTERNAL_SERVER_ERROR.value();
        HttpStatus status = HttpStatus.resolve(statusValue);
        if (status == null || status.is2xxSuccessful()) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        ErrorCode code = ErrorCode.forStatus(status.value());
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = uri != null ? uri.toString() : request.getRequestURI();

        String message = status.is5xxServerError()
                ? "An unexpected error occurred. Please try again later."
                : status.getReasonPhrase();
        log.warn("Container-level error {} on {}", status.value(), path);

        return ResponseEntity.status(status)
                .body(ApiEnvelope.error(status, code.name(), message, List.of(), path));
    }
}
