package com.aigreentick.services.template.application.dto.client;


import lombok.Data;

@Data
public class FacebookApiResponse<T> {
    private boolean success;
    private T data;
    private String errorMessage;
    private int statusCode;

    /**
     * {@link #statusCode} value meaning Meta sent no HTTP response at all
     * (timeout, connection reset). The request may or may not have been
     * processed by Meta.
     */
    public static final int NO_RESPONSE = 0;

    private FacebookApiResponse() {
        // prevent accidental raw instantiation
    }

    public static <T> FacebookApiResponse<T> success(T data, int statusCode) {
        FacebookApiResponse<T> response = new FacebookApiResponse<>();
        response.success = true;
        response.data = data;
        response.statusCode = statusCode;
        return response;
    }

    /** True when Meta answered with a 4xx: a definitive rejection of the request. */
    public boolean isClientError() {
        return !success && statusCode >= 400 && statusCode < 500;
    }

    public static <T> FacebookApiResponse<T> error(String errorMessage, int statusCode) {
        FacebookApiResponse<T> response = new FacebookApiResponse<>();
        response.success = false;
        response.errorMessage = errorMessage;
        response.statusCode = statusCode;
        return response;
    }
}
