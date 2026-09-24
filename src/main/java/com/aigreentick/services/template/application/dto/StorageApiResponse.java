package com.aigreentick.services.template.application.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * storage-service response wrapper — the company API Standard shape
 * {@code {success, status, code, message, data, errors, meta}}.
 * Only the fields this service reads are mapped.
 */
@Data
@NoArgsConstructor
public class StorageApiResponse<T> {

    /** True only for 2xx. */
    private Boolean success;
    /** Always equals the HTTP status. */
    private Integer status;
    /** {@code SUCCESS}, or an error code such as {@code QUOTA_NOT_PROVISIONED}. */
    private String code;
    private String message;
    private T data;

    /** Deliberately not a bean-style name, so Jackson does not treat it as a property. */
    public boolean succeeded() {
        return Boolean.TRUE.equals(success);
    }
}
