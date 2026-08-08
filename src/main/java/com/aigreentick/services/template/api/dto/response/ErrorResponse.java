package com.aigreentick.services.template.api.dto.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standardized error response body.
 * Used by {@link com.aigreentick.services.template.api.advice.GlobalExceptionHandler}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private String status;
    private int code;
    private String message;
    private String path;
    private Instant timestamp;

    /**
     * Present only for validation errors (400).
     * Each entry describes one field that failed validation.
     */
    private List<FieldError> fieldErrors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}