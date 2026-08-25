package com.aigreentick.services.template.infrastructure.client.media;

import com.aigreentick.services.template.application.dto.BatchUploadResult;
import com.aigreentick.services.template.application.dto.StorageApiResponse;
import com.aigreentick.services.template.application.port.out.InternalMediaPort;
import com.aigreentick.services.template.application.service.DownloadedMediaTask;
import com.aigreentick.services.template.common.constant.ApiHeaders;
import com.aigreentick.services.template.infrastructure.config.WebClientConfig;
import com.aigreentick.services.template.infrastructure.config.properties.MediaServiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Uploads media to storage-service.
 *
 * <p>
 * Contract:
 *
 * <ul>
 * <li>POST /api/v1/media/upload/batch</li>
 * <li>Multipart parts must be named {@code files}</li>
 * <li>Each file is sent as {@code application/octet-stream}</li>
 * <li>Tenancy headers: X-Org-Id, X-Project-Id and X-Waba-Id</li>
 * <li>Response is wrapped in {@code {status, message, data}} and returns
 * HTTP 207 on every outcome, including all-success</li>
 * </ul>
 *
 * <p>
 * <b>Results are joined to tasks by POSITION, not by filename.</b>
 * storage-service guarantees that {@code data.results} is in request
 * order with exactly one entry per submitted file, so
 * {@code results.get(i)} always corresponds to {@code tasks.get(i)}.
 * Filenames are NOT a safe join key: two media tasks in one batch can
 * carry the same upload filename, and joining on it would silently
 * attach the wrong URL to the wrong template. This adapter therefore
 * verifies the returned cardinality before handing the result on, and
 * refuses the batch if it does not line up.
 *
 * <p>
 * The original upload filename is still sent on every part, because
 * storage-service echoes it back verbatim in
 * {@code results[].originalFilename} and it is what appears in its logs
 * and audit trail. It is a label, not an identifier.
 *
 * <p>
 * Media synchronization is best-effort. If storage-service cannot
 * process the batch, this adapter returns {@code null} so template
 * synchronization can continue without an internal media URL.
 */
@Slf4j
@Component
public class InternalMediaAdapter implements InternalMediaPort {

    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final String FILE_PART_NAME = "files";

    /**
     * Storage-service accepts uploaded binary media using the generic
     * binary MIME type.
     *
     * <p>
     * Using this explicitly is important, and it is not merely a
     * fallback for files with an unreliable extension. storage-service
     * treats a declared {@code application/octet-stream} as "no claim
     * made" and derives the real type from the file's magic bytes via
     * Tika, then enforces its allowlist against THAT. Declaring any
     * concrete type instead adds a constraint: the declared type must
     * then match the detected type exactly, or the file is rejected
     * with {@code CONTENT_TYPE_MISMATCH}.
     *
     * <p>
     * Since the temp file has no dependable extension or content type,
     * declaring nothing is both the safest and the intended input. Do
     * not "improve" this by probing the file and sending a specific
     * type — that can only ever cause rejections, never prevent them.
     *
     * <p>
     * Note this is the content type of each PART. The request-level
     * content type is {@code multipart/form-data}, set separately
     * below; setting that value on a part instead produces a per-file
     * {@code CONTENT_TYPE_NOT_ALLOWED}.
     *
     * <p>
     * <b>Setting this alone is not enough.</b> Spring's
     * {@code ResourceHttpMessageWriter} treats an explicit
     * {@code application/octet-stream} on a {@code Resource} part as
     * "not really specified" and re-derives the type from the
     * resource's filename:
     *
     * <pre>
     * if (mediaType != null &amp;&amp; mediaType.isConcrete()
     *         &amp;&amp; !mediaType.equals(MediaType.APPLICATION_OCTET_STREAM)) {
     *     return mediaType;                            // octet-stream fails this
     * }
     * return MediaTypeFactory.getMediaType(resource)... // guesses from the extension
     * </pre>
     *
     * See {@link #unnamed(java.io.File)} for how that is defeated.
     */
    private static final MediaType MEDIA_CONTENT_TYPE = MediaType.APPLICATION_OCTET_STREAM;

    private final WebClient webClient;
    private final MediaServiceProperties properties;

    public InternalMediaAdapter(
            @Qualifier(WebClientConfig.MEDIA_WEB_CLIENT) WebClient webClient,
            MediaServiceProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public BatchUploadResult uploadBatch(
            List<DownloadedMediaTask> tasks,
            Long orgId,
            Long projectId,
            String wabaId) {

        if (tasks == null || tasks.isEmpty()) {
            log.warn("uploadBatch called with no tasks - nothing to upload");
            return null;
        }

        int maxFiles = properties.getBatch().getMaxFiles();

        if (tasks.size() > maxFiles) {
            log.error(
                    "Batch of {} file(s) exceeds media-service.batch.max-files={}; "
                            + "refusing to send. orgId={} projectId={}",
                    tasks.size(),
                    maxFiles,
                    orgId,
                    projectId);

            return null;
        }

        String path = properties.getBatch().getUploadPath();

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        for (DownloadedMediaTask task : tasks) {

            bodyBuilder
                    .part(FILE_PART_NAME, unnamed(task.getTempFile()))
                    .filename(task.getUploadFilename())
                    .contentType(MEDIA_CONTENT_TYPE);
        }

        log.info(
                "Uploading batch of {} file(s) to storage-service. "
                        + "orgId={} projectId={} wabaId={} path={} contentType={}",
                tasks.size(),
                orgId,
                projectId,
                wabaId,
                path,
                MEDIA_CONTENT_TYPE);

        try {

            StorageApiResponse<BatchUploadResult> envelope = webClient.post()
                    .uri(path)
                    .header(
                            ApiHeaders.ORG_ID,
                            String.valueOf(orgId))
                    .header(
                            ApiHeaders.PROJECT_ID,
                            String.valueOf(projectId))
                    .header(
                            ApiHeaders.WABA_ID,
                            wabaId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(
                            BodyInserters.fromMultipartData(
                                    bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(
                            new ParameterizedTypeReference<StorageApiResponse<BatchUploadResult>>() {
                            })
                    .block();

            return unwrap(
                    envelope,
                    tasks.size(),
                    orgId,
                    projectId);

        } catch (Exception ex) {

            log.error(
                    "Batch upload to storage-service failed. "
                            + "orgId={} projectId={} wabaId={} fileCount={}",
                    orgId,
                    projectId,
                    wabaId,
                    tasks.size(),
                    ex);

            return null;
        }
    }

    /**
     * A file resource that refuses to name itself.
     *
     * <p>This is what makes {@link #MEDIA_CONTENT_TYPE} actually take effect.
     * Spring discards an explicit {@code application/octet-stream} on a
     * {@code Resource} part and asks {@code MediaTypeFactory} to guess from
     * {@code resource.getFilename()} instead. Returning null leaves it nothing
     * to guess from, so the fallback IS {@code application/octet-stream} and the
     * declared type finally matches what this adapter intends to send.
     *
     * <p>Without this, every image went out declared {@code image/jpeg} —
     * because the temp filenames carry a fabricated {@code .jpg} suffix on all
     * images regardless of their real format — and storage-service rejected
     * every PNG and WebP among them with {@code CONTENT_TYPE_MISMATCH}. The
     * bytes said PNG, the declaration said JPEG, and it was right to refuse.
     *
     * <p>Nulling the name is safe here and deliberately narrow: the
     * Content-Disposition filename is set separately by {@code .filename()} on
     * the part builder, so storage-service still echoes back the correct
     * {@code originalFilename}. This only removes the extension-based GUESS.
     */
    private static Resource unnamed(java.io.File file) {
        return new FileSystemResource(file) {
            @Override
            public String getFilename() {
                return null;
            }
        };
    }

    /**
     * Unwraps storage-service's response envelope and validates the
     * response structure.
     *
     * <p>
     * The cardinality check is the important one. Callers join results
     * to tasks by index, and that is only sound while
     * {@code results.size() == fileCount}. storage-service guarantees
     * it; this verifies it, so a contract change on the other side
     * becomes a refused batch and a log line rather than a set of
     * templates quietly pointing at each other's media.
     */
    private BatchUploadResult unwrap(
            StorageApiResponse<BatchUploadResult> envelope,
            int fileCount,
            Long orgId,
            Long projectId) {

        if (envelope == null) {

            log.error(
                    "storage-service returned an empty body for batch upload. "
                            + "orgId={} projectId={}",
                    orgId,
                    projectId);

            return null;
        }

        if (!SUCCESS_STATUS.equals(envelope.getStatus())) {

            log.error(
                    "storage-service rejected the batch. "
                            + "status='{}' message='{}' orgId={} projectId={}",
                    envelope.getStatus(),
                    envelope.getMessage(),
                    orgId,
                    projectId);

            return null;
        }

        BatchUploadResult result = envelope.getData();

        if (result == null) {

            log.error(
                    "storage-service returned {} with no data. "
                            + "orgId={} projectId={}",
                    SUCCESS_STATUS,
                    orgId,
                    projectId);

            return null;
        }

        if (result.getResults() == null) {

            log.error(
                    "storage-service returned {} with a null results list; "
                            + "cannot join {} file(s) to tasks. orgId={} projectId={}",
                    SUCCESS_STATUS,
                    fileCount,
                    orgId,
                    projectId);

            return null;
        }

        // Positional join guard. A short, long or empty list means the
        // index-to-task correspondence no longer holds, and continuing would
        // attach media URLs to the wrong templates with no error anywhere.
        // Refusing the whole batch is the only safe outcome: media sync is
        // best-effort, so a null here costs a retry, while a bad join corrupts
        // data that nothing downstream will ever re-verify.
        if (result.getResults().size() != fileCount) {

            log.error(
                    "storage-service returned {} result(s) for {} submitted file(s); "
                            + "positional join is unsafe, discarding the batch. "
                            + "successCount={} failedCount={} orgId={} projectId={}",
                    result.getResults().size(),
                    fileCount,
                    result.getSuccessCount(),
                    result.getFailedCount(),
                    orgId,
                    projectId);

            return null;
        }

        if (result.getFailedCount() > 0) {

            log.warn(
                    "Batch upload partially failed. "
                            + "successCount={} failedCount={} sent={} "
                            + "orgId={} projectId={}",
                    result.getSuccessCount(),
                    result.getFailedCount(),
                    fileCount,
                    orgId,
                    projectId);

        } else {

            log.info(
                    "Batch upload complete. "
                            + "successCount={} sent={} orgId={} projectId={}",
                    result.getSuccessCount(),
                    fileCount,
                    orgId,
                    projectId);
        }

        return result;
    }
}