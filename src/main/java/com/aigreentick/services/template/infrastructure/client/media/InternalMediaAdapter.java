package com.aigreentick.services.template.infrastructure.client.media;

import java.net.URI;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.aigreentick.services.template.application.dto.BatchUploadResult;
import com.aigreentick.services.template.application.dto.StorageApiResponse;
import com.aigreentick.services.template.application.port.out.InternalMediaPort;
import com.aigreentick.services.template.application.service.DownloadedMediaTask;
import com.aigreentick.services.template.infrastructure.config.MediaServiceProperties;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class InternalMediaAdapter implements InternalMediaPort {

    private final WebClient webClient;
    private final MediaServiceProperties properties;

    public InternalMediaAdapter(WebClient.Builder webClientBuilder, MediaServiceProperties properties) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public BatchUploadResult uploadBatch(
            List<DownloadedMediaTask> tasks,
            Long orgId,
            Long projectId,
            String wabaId) {

        if (tasks == null || tasks.isEmpty()) {
            log.warn("uploadBatch called with empty task list");
            return null;
        }

        URI uri = URI.create(properties.getBaseUrl() + properties.getBatch().getUploadPath());

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        for (DownloadedMediaTask task : tasks) {
            bodyBuilder.part("files", new FileSystemResource(task.getTempFile()))
                    .filename(task.getUploadFilename());
        }

        log.info("Uploading batch of {} file(s) to storage service. orgId={} projectId={} uri={}",
                tasks.size(), orgId, projectId, uri);

        try {

            StorageApiResponse<BatchUploadResult> envelope = webClient
                    .post()
                    .uri(uri)
                    .header("X-Org-Id", String.valueOf(orgId))
                    .header("X-Project-Id", String.valueOf(projectId))
                    .header("X-Waba-Id", wabaId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<StorageApiResponse<BatchUploadResult>>() {})
                    .block();

            if (envelope == null) {
                log.error("Storage service returned null envelope for batch upload. orgId={} projectId={}",
                        orgId, projectId);
                return null;
            }

            if (!"SUCCESS".equals(envelope.getStatus())) {
                log.error("Storage service returned non-success status='{}' message='{}' for batch upload",
                        envelope.getStatus(), envelope.getMessage());
                return null;
            }

            BatchUploadResult result = envelope.getData();

            if (result == null) {
                log.error("Storage service returned SUCCESS status but null data. orgId={} projectId={}",
                        orgId, projectId);
                return null;
            }

            if (result.getResults() == null || result.getResults().isEmpty()) {
                log.warn("Storage service returned empty results list. successCount={} failedCount={}",
                        result.getSuccessCount(), result.getFailedCount());
                return result;
            }

            log.info("Batch upload complete. successCount={} failedCount={} totalFiles={}",
                    result.getSuccessCount(), result.getFailedCount(), tasks.size());

            return result;

        } catch (Exception e) {
            log.error("Batch upload to storage service failed. orgId={} projectId={} uri={}",
                    orgId, projectId, uri, e);
            return null;
        }
    }
}