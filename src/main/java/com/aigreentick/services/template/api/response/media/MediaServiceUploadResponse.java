package com.aigreentick.services.template.api.response.media;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaServiceUploadResponse {

    private String url;
    private String originalFilename;
    private String storedFilename;
    private String mediaType;
    private String contentType;
    private long fileSizeBytes;
    private String mediaId;
    private Instant uploadedAt;
}