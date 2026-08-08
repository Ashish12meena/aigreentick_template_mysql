package com.aigreentick.services.template.application.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StorageApiResponse<T> {
    private String status;
    private String message;
    private T data;
}