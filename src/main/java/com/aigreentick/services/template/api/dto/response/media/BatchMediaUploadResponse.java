package com.aigreentick.services.template.api.dto.response.media;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchMediaUploadResponse {

    private String status;
    private String message;
    private BatchData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchData {
        private int successCount;
        private int failedCount;
        private List<MediaServiceUploadResponse> results;
    }
}