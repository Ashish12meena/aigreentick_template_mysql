package com.aigreentick.services.template.application.service;

import com.aigreentick.services.template.infrastructure.config.MediaSyncThreadPoolConfig;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.aigreentick.services.template.application.dto.BatchUploadResult;
import com.aigreentick.services.template.application.port.out.InternalMediaPort;
import com.aigreentick.services.template.domain.enums.ComponentFormat;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselCard;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselCardComponent;
import com.aigreentick.services.template.domain.model.WhatsappTemplateComponent;
import com.aigreentick.services.template.infrastructure.config.properties.MediaServiceProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles media re-hosting during Facebook template sync.
 *
 * Two-phase approach:
 * Phase 1 — Download all media from Facebook IN PARALLEL (dedicated thread
 * pool)
 * Phase 2 — Upload all downloaded files to storage service in a SINGLE BATCH
 * call
 *
 * Uses a dedicated {@code mediaSyncExecutor} thread pool instead of Reactor's
 * boundedElastic scheduler. This isolates blocking I/O from the rest of the
 * application and prevents thread starvation under heavy sync load.
 *
 * <p><b>Media sync is best-effort and NOT transactional.</b> A template whose
 * media fails to re-host keeps its original Facebook URL and is still persisted.
 * That is intentional, but it means a silent failure here is invisible in the
 * template data — so every path that loses a URL logs why, at WARN or ERROR.
 */
@Service
@Slf4j
public class MediaSyncService {

    // private final MediaSyncClient mediaClient;
    // After
    private final FacebookMediaDownloadService mediaDownloader;
    private final InternalMediaPort mediaUploader;
    private final MediaServiceProperties batchConfig;
    private final Executor mediaSyncExecutor;

    public MediaSyncService(
            FacebookMediaDownloadService mediaDownloader,
            InternalMediaPort mediaUploader,
            MediaServiceProperties batchConfig,
            @Qualifier(MediaSyncThreadPoolConfig.MEDIA_SYNC_EXECUTOR) Executor mediaSyncExecutor) {
        this.mediaDownloader = mediaDownloader;
        this.mediaUploader = mediaUploader;
        this.batchConfig = batchConfig;
        this.mediaSyncExecutor = mediaSyncExecutor;
    }

    // ── Public API (signatures unchanged) ──

    /**
     * Resolves all media in a single synced template.
     * Mutates the template entity in place (sets mediaUrl on components).
     */
    public void resolveMediaForTemplate(
            WhatsappTemplate template, Long orgId, Long projectId, String wabaId) {

        List<MediaTask> tasks = collectMediaTasks(template, orgId, projectId, wabaId);

        if (tasks.isEmpty()) {
            log.debug("No media to resolve for template: {}", template.getName());
            return;
        }

        log.info("Resolving {} media asset(s) for template: {}",
                tasks.size(), template.getName());

        long start = System.currentTimeMillis();
        executeTasks(tasks, orgId, projectId, wabaId);
        log.info("Media resolution complete for template: {} in {}ms",
                template.getName(), System.currentTimeMillis() - start);
    }

    /**
     * Batch version: resolves media for multiple templates.
     */
    public void resolveMediaForTemplates(
            List<WhatsappTemplate> templates, Long orgId, Long projectId, String wabaId) {

        List<MediaTask> allTasks = new ArrayList<>();
        for (WhatsappTemplate t : templates) {
            allTasks.addAll(collectMediaTasks(t, orgId, projectId, wabaId));
        }

        if (allTasks.isEmpty()) {
            log.debug("No media to resolve across {} templates", templates.size());
            return;
        }

        log.info("Resolving {} media asset(s) across {} templates",
                allTasks.size(), templates.size());

        long start = System.currentTimeMillis();
        executeTasks(allTasks, orgId, projectId, wabaId);
        log.info("Batch media resolution complete for {} templates in {}ms",
                templates.size(), System.currentTimeMillis() - start);
    }

    // ── Core two-phase execution ──

    private void executeTasks(List<MediaTask> tasks, Long orgId, Long projectId, String wabaId) {
        List<File> allTempFiles = new ArrayList<>();

        try {
            // ─── Phase 1: Parallel download from Facebook ───
            long dlStart = System.currentTimeMillis();

            List<DownloadedMediaTask> downloaded = downloadAllParallel(tasks);

            if (downloaded.isEmpty()) {
                log.warn("No media files downloaded successfully");
                return;
            }

            // Track all temp files for cleanup
            downloaded.forEach(d -> allTempFiles.add(d.getTempFile()));

            log.info("Phase 1 complete: downloaded {}/{} files in {}ms",
                    downloaded.size(), tasks.size(), System.currentTimeMillis() - dlStart);

            // ─── Phase 2: Chunk by size ───
            List<List<DownloadedMediaTask>> chunks = chunkBySize(downloaded);
            log.info("Phase 2: split into {} chunk(s)", chunks.size());

            // ─── Phase 3: Batch upload per chunk ───
            int totalMapped = 0;

            for (int i = 0; i < chunks.size(); i++) {
                List<DownloadedMediaTask> chunk = chunks.get(i);
                long upStart = System.currentTimeMillis();

                BatchUploadResult response = mediaUploader.uploadBatch(chunk, orgId, projectId, wabaId);

                log.info("Phase 3: chunk {}/{} uploaded ({} files) in {}ms",
                        i + 1, chunks.size(), chunk.size(), System.currentTimeMillis() - upStart);

                // ─── Phase 4: Map results back to entities ───
                totalMapped += mapResultsToTasks(response, chunk);
            }

            // The number that actually matters. Everything above counts files
            // moved; this counts templates that will point at our own storage.
            // Without it the sync logs a confident "complete" whether it resolved
            // every URL or none of them.
            if (totalMapped == downloaded.size()) {
                log.info("Phase 4 complete: {}/{} media URL(s) resolved",
                        totalMapped, downloaded.size());
            } else {
                log.warn("Phase 4 complete: {}/{} media URL(s) resolved - {} template component(s) "
                                + "keep their original Facebook URL",
                        totalMapped, downloaded.size(), downloaded.size() - totalMapped);
            }

        } finally {
            // ─── Phase 5: Cleanup all temp files ───
            for (File f : allTempFiles) {
                if (f != null && f.exists() && !f.delete()) {
                    log.warn("Failed to delete temp file: {}", f.getAbsolutePath());
                }
            }
            log.debug("Cleaned up {} temp files", allTempFiles.size());
        }
    }

    // ── Phase 1: Parallel download using dedicated thread pool ──

    /**
     * Downloads all media files from Facebook in parallel using the dedicated
     * mediaSyncExecutor pool. Each download runs as a CompletableFuture.
     *
     * Failed downloads are logged and skipped — they don't block other downloads.
     */
    private List<DownloadedMediaTask> downloadAllParallel(List<MediaTask> tasks) {

        // Submit all download tasks to the dedicated pool
        List<CompletableFuture<DownloadedMediaTask>> futures = new ArrayList<>(tasks.size());

        for (int i = 0; i < tasks.size(); i++) {
            final MediaTask task = tasks.get(i);
            final int index = i;

            CompletableFuture<DownloadedMediaTask> future = CompletableFuture
                    .supplyAsync(() -> downloadSingle(task, index), mediaSyncExecutor)
                    .exceptionally(ex -> {
                        log.warn("Download failed for {}: {}", task.sourceUrl, ex.getMessage());
                        return null; // skip failed downloads
                    });

            futures.add(future);
        }

        // Wait for all downloads to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Collect successful results, filter out nulls (failed downloads)
        List<DownloadedMediaTask> results = new ArrayList<>(tasks.size());
        for (CompletableFuture<DownloadedMediaTask> future : futures) {
            DownloadedMediaTask result = future.join();
            if (result != null) {
                results.add(result);
            }
        }

        return results;
    }

    /**
     * Downloads a single media file from Facebook to a temp file.
     * Runs on the mediaSyncExecutor thread pool.
     */
    private DownloadedMediaTask downloadSingle(MediaTask task, int index) {
        long start = System.currentTimeMillis();
        String ext = resolveExtension(task.mediaType);
        String uploadFilename = index + "_" + task.mediaType + ext;

        try {
            File tempFile = mediaDownloader.downloadToTempFile(task.sourceUrl, task.mediaType);

            log.debug("Downloaded [{}] in {}ms ({} bytes): {}",
                    uploadFilename, System.currentTimeMillis() - start,
                    tempFile.length(), task.sourceUrl);

            return new DownloadedMediaTask(task, tempFile, index, uploadFilename);
        } catch (Exception e) {
            throw new RuntimeException("Failed to download: " + task.sourceUrl, e);
        }
    }

    // ── Phase 2: Chunking ──

    private List<List<DownloadedMediaTask>> chunkBySize(List<DownloadedMediaTask> downloaded) {
        List<List<DownloadedMediaTask>> chunks = new ArrayList<>();
        List<DownloadedMediaTask> current = new ArrayList<>();
        long currentBytes = 0;

        for (DownloadedMediaTask task : downloaded) {
            long fileSize = task.getTempFile().length();

            if (!current.isEmpty()
                    && (currentBytes + fileSize > batchConfig.getBatch().getMaxBytes()
                            || current.size() >= batchConfig.getBatch().getMaxFiles())) {
                chunks.add(current);
                current = new ArrayList<>();
                currentBytes = 0;
            }

            current.add(task);
            currentBytes += fileSize;
        }

        if (!current.isEmpty()) {
            chunks.add(current);
        }

        return chunks;
    }

    // ── Phase 4: Result mapping ──

    /**
     * Writes each uploaded URL back onto its originating task.
     *
     * <p><b>Matched by POSITION, not by filename.</b> storage-service guarantees
     * {@code results} is in request order with exactly one entry per submitted
     * file, and that is the documented join. The previous filename-keyed
     * {@code Collectors.toMap} happened to work only because
     * {@link #downloadSingle} prefixes every upload filename with a globally
     * unique index — change that naming, or reuse a name inside one chunk, and
     * {@code toMap} throws {@code IllegalStateException} on the duplicate key and
     * kills the chunk. Positional matching carries no such hidden dependency.
     *
     * <p>The cardinality check is what makes the position safe to trust. If it
     * does not hold, nothing is mapped: attaching media URLs to the wrong
     * templates is silent, permanent, and nothing downstream re-verifies it,
     * whereas mapping nothing costs one re-sync.
     *
     * @return how many URLs were actually applied
     */
    private int mapResultsToTasks(BatchUploadResult response, List<DownloadedMediaTask> chunk) {
        if (response == null) {
            // The adapter already logged the cause with a stack trace. This line
            // records the CONSEQUENCE, which the adapter cannot see: these
            // specific template components keep their Facebook URLs.
            log.error("Batch upload returned null response - {} file(s) in this chunk keep no media URL",
                    chunk.size());
            return 0;
        }

        List<BatchUploadResult.BatchFileResult> results = response.getResults();

        if (results == null || results.size() != chunk.size()) {
            log.error("Batch returned {} result(s) for {} submitted file(s); positional join is unsafe, "
                            + "discarding chunk. successCount={} failedCount={}",
                    results == null ? "null" : String.valueOf(results.size()), chunk.size(),
                    response.getSuccessCount(), response.getFailedCount());
            return 0;
        }

        Map<String, Integer> failureCodes = new LinkedHashMap<>();
        int mapped = 0;
        int retryable = 0;

        for (int i = 0; i < results.size(); i++) {
            BatchUploadResult.BatchFileResult result = results.get(i);
            DownloadedMediaTask task = chunk.get(i);

            // Tripwire on the ordering assumption. Upload filenames are unique
            // per run, so a mismatch here means the contract changed and every
            // URL in this chunk would land on the wrong template component.
            if (result.getOriginalFilename() != null
                    && !result.getOriginalFilename().equals(task.getUploadFilename())) {
                log.error("Result order mismatch at index {}: storage returned '{}', expected '{}'. "
                                + "Discarding chunk rather than risk mis-assigning media URLs.",
                        i, result.getOriginalFilename(), task.getUploadFilename());
                return mapped;
            }

            if (result.isSuccess() && result.getUrl() != null) {
                task.getMediaTask().applyUrl(result.getUrl());
                mapped++;
                log.debug("Mapped URL for file='{}' url='{}'", task.getUploadFilename(), result.getUrl());
                continue;
            }

            // A SUCCESS carrying no URL is not a success. Counting it as one would
            // leave the component pointing at Facebook while the totals claim
            // everything resolved — the failure would be invisible.
            String code = result.isSuccess()
                    ? "SUCCESS_WITHOUT_URL"
                    : (result.errorCode() == null ? "UNSPECIFIED" : result.errorCode());
            failureCodes.merge(code, 1, Integer::sum);

            if (result.getStatus() == BatchUploadResult.BatchFileResult.Status.SKIPPED) {
                retryable++;
            }

            log.warn("File '{}' not stored: status={} code={} message={}",
                    task.getUploadFilename(), result.getStatus(), code, result.errorMessage());
        }

        if (failureCodes.isEmpty()) {
            log.info("Chunk mapped: {}/{} media URL(s) applied", mapped, chunk.size());
        } else {
            // The histogram IS the diagnosis. QUOTA_NOT_PROVISIONED means ops must
            // provision the project; CONTENT_TYPE_NOT_ALLOWED means the allowlist
            // or the file itself; BATCH_ITEM_SKIPPED means storage was down and a
            // plain re-sync fixes it. Counters alone cannot separate those, and
            // that ambiguity is what made the last outage take days to find.
            log.warn("Chunk mapped: {}/{} media URL(s) applied; {} not stored {}{}",
                    mapped, chunk.size(), chunk.size() - mapped, failureCodes,
                    retryable > 0 ? " (" + retryable + " skipped, safe to re-sync)" : "");
        }

        return mapped;
    }

    // ── Task collection (UNCHANGED) ──

    List<MediaTask> collectMediaTasks(
            WhatsappTemplate template, Long orgId, Long projectId, String wabaId) {

        List<MediaTask> tasks = new ArrayList<>();
        if (template.getComponents() == null)
            return tasks;

        for (WhatsappTemplateComponent comp : template.getComponents()) {
            collectComponentMediaTask(comp, orgId, projectId, wabaId, tasks);
            collectCarouselMediaTasks(comp, orgId, projectId, wabaId, tasks);
        }

        return tasks;
    }

    private void collectComponentMediaTask(
            WhatsappTemplateComponent comp,
            Long orgId, Long projectId, String wabaId,
            List<MediaTask> tasks) {

        if (!isMediaFormat(comp.getFormat()))
            return;

        if (hasMediaHandle(comp)) {
            tasks.add(new MediaTask(
                    comp.getMediaHandle(), comp.getFormat().name(),
                    orgId, projectId, wabaId,
                    url -> comp.setMediaUrl(url)));
            return;
        }

        String headerHandle = extractHeaderHandle(comp);
        if (headerHandle != null) {
            tasks.add(new MediaTask(
                    headerHandle, comp.getFormat().name(),
                    orgId, projectId, wabaId,
                    url -> comp.setMediaUrl(url)));
        }
    }

    private void collectCarouselMediaTasks(
            WhatsappTemplateComponent comp,
            Long orgId, Long projectId, String wabaId,
            List<MediaTask> tasks) {

        if (comp.getCarouselCards() == null)
            return;

        for (WhatsappTemplateCarouselCard card : comp.getCarouselCards()) {
            if (card.getCardComponents() == null)
                continue;

            for (WhatsappTemplateCarouselCardComponent cc : card.getCardComponents()) {
                String mediaType = cc.getFormat() != null ? cc.getFormat().name() : "IMAGE";

                if (hasCarouselMediaHandle(cc)) {
                    tasks.add(new MediaTask(
                            cc.getMediaHandle(), mediaType,
                            orgId, projectId, wabaId,
                            url -> cc.setMediaUrl(url)));
                    continue;
                }

                String handle = extractCarouselHeaderHandle(cc);
                if (handle != null) {
                    tasks.add(new MediaTask(
                            handle, mediaType,
                            orgId, projectId, wabaId,
                            url -> cc.setMediaUrl(url)));
                }
            }
        }
    }

    // ── Helpers (UNCHANGED) ──

    private boolean isMediaFormat(ComponentFormat format) {
        return format == ComponentFormat.IMAGE
                || format == ComponentFormat.VIDEO
                || format == ComponentFormat.DOCUMENT;
    }

    private boolean hasMediaHandle(WhatsappTemplateComponent comp) {
        return comp.getMediaHandle() != null && !comp.getMediaHandle().isBlank();
    }

    private boolean hasCarouselMediaHandle(WhatsappTemplateCarouselCardComponent cc) {
        return cc.getMediaHandle() != null && !cc.getMediaHandle().isBlank();
    }

    private String extractHeaderHandle(WhatsappTemplateComponent comp) {
        if (comp.getExample() == null)
            return null;
        if (comp.getExample().getHeaderHandle() == null)
            return null;
        if (comp.getExample().getHeaderHandle().isEmpty())
            return null;
        String handle = comp.getExample().getHeaderHandle().get(0);
        return (handle != null && !handle.isBlank()) ? handle : null;
    }

    private String extractCarouselHeaderHandle(WhatsappTemplateCarouselCardComponent cc) {
        if (cc.getExample() == null)
            return null;
        if (cc.getExample().getHeaderHandle() == null)
            return null;
        if (cc.getExample().getHeaderHandle().isEmpty())
            return null;
        String handle = cc.getExample().getHeaderHandle().get(0);
        return (handle != null && !handle.isBlank()) ? handle : null;
    }

    /**
     * Suffix for the upload filename — a HINT for content detection, nothing more.
     *
     * <p>This extension no longer influences the declared content type. That path
     * was closed in {@code InternalMediaAdapter.unnamed(File)}, which strips the
     * resource name so Spring cannot derive a type from it and the explicit
     * {@code application/octet-stream} survives to the wire.
     *
     * <p>What the extension still does is feed storage-service's Tika inspector,
     * which sets it as {@code RESOURCE_NAME_KEY}. There, a filename hint only
     * REFINES a magic-byte match and can never override one — so a PNG named
     * {@code .jpg} is still correctly detected as {@code image/png}, while an MP4
     * container that magic bytes alone leave ambiguous gets resolved to
     * {@code video/mp4} instead of falling outside the allowlist.
     *
     * <p>That second case is not hypothetical: briefly returning {@code .bin} for
     * everything removed the hint and turned 13 previously-fine videos into
     * {@code CONTENT_TYPE_NOT_ALLOWED}. The hint is load-bearing for video and
     * costs nothing for anything else.
     */
    private String resolveExtension(String mediaType) {
        if (mediaType == null)
            return ".bin";
        return switch (mediaType.toUpperCase()) {
            case "VIDEO" -> ".mp4";
            case "DOCUMENT" -> ".pdf";
            case "IMAGE" -> ".jpg";
            default -> ".bin";
        };
    }

    // ── Inner types ──

    /**
     * Encapsulates a single media download+upload task with a callback
     * to write the resulting URL back into the correct entity field.
     */
    static class MediaTask {
        final String sourceUrl;
        final String mediaType;
        final Long orgId;
        final Long projectId;
        final String wabaId;
        private final Consumer<String> urlSetter;

        MediaTask(String sourceUrl, String mediaType,
                Long orgId, Long projectId, String wabaId,
                Consumer<String> urlSetter) {
            this.sourceUrl = sourceUrl;
            this.mediaType = mediaType;
            this.orgId = orgId;
            this.projectId = projectId;
            this.wabaId = wabaId;
            this.urlSetter = urlSetter;
        }

        void applyUrl(String url) {
            if (url != null) {
                urlSetter.accept(url);
            }
        }
    }
}