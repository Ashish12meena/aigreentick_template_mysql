package com.aigreentick.services.template.infrastructure.client.account;

import lombok.Getter;
import lombok.Setter;

/**
 * waba-service response wrapper - the company API Standard shape
 * {@code {success, status, code, message, data, errors, meta}}.
 * Only the fields this client reads are mapped; the payload is in {@code data}.
 */
@Getter
@Setter
public class WabaApiEnvelope<T> {
    private Boolean success;
    private Integer status;
    private String code;
    private String message;
    private T data;

    public boolean succeeded() {
        return Boolean.TRUE.equals(success);
    }
}