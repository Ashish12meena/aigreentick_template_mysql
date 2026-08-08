package com.aigreentick.services.template.application.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadResult {
    private int successCount;
    private int failedCount;
    private List<BatchFileResult> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchFileResult {

        public enum Status { SUCCESS, FAILED }

        private String originalFilename;
        private Status status;   // enum, matches storage service exactly
        private String url;
        private String mediaType;
        private String contentType;
        private Long fileSizeBytes;
        private String error;
    }
}