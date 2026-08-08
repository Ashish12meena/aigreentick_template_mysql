package com.aigreentick.services.template.application.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.aigreentick.services.template.application.dto.BatchUploadResult;
import com.aigreentick.services.template.application.port.out.InternalMediaPort;
import com.aigreentick.services.template.domain.enums.ComponentFormat;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselCard;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselCardComponent;
import com.aigreentick.services.template.domain.model.WhatsappTemplateComponent;
import com.aigreentick.services.template.infrastructure.config.MediaServiceProperties;
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
 */
@Service
@Slf4j
public class MediaSyncService {

    // private final MediaSyncClient mediaClient;
    // After
    private final FacebookMediaDownloadService mediaDownloader;
    private final InternalMediaPort mediaUploader;
    private final MediaServiceProperties batchConfig;
    private final ExecutorService mediaSyncExecutor;

    public MediaSyncService(
            FacebookMediaDownloadService mediaDownloader,
            InternalMediaPort mediaUploader,
            MediaServiceProperties batchConfig,
            @Qualifier("mediaSyncExecutor") ExecutorService mediaSyncExecutor) {
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
            for (int i = 0; i < chunks.size(); i++) {
                List<DownloadedMediaTask> chunk = chunks.get(i);
                long upStart = System.currentTimeMillis();

                BatchUploadResult response = mediaUploader.uploadBatch(chunk, orgId, projectId, wabaId);

                log.info("Phase 3: chunk {}/{} uploaded ({} files) in {}ms",
                        i + 1, chunks.size(), chunk.size(), System.currentTimeMillis() - upStart);

                // ─── Phase 4: Map results back to entities ───
                mapResultsToTasks(response, chunk);
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

    private void mapResultsToTasks(BatchUploadResult response, List<DownloadedMediaTask> chunk) {
        if (response == null) {
            log.error("Batch upload returned null response");
            return;
        }

        if (response.getResults() == null || response.getResults().isEmpty()) {
            log.warn("Batch upload returned no results. successCount={} failedCount={}",
                    response.getSuccessCount(), response.getFailedCount());
            return;
        }

        Map<String, DownloadedMediaTask> tasksByFilename = chunk.stream()
                .collect(Collectors.toMap(DownloadedMediaTask::getUploadFilename, t -> t));

        for (BatchUploadResult.BatchFileResult result : response.getResults()) {
            if (result.getOriginalFilename() == null) {
                log.warn("Batch result missing originalFilename, skipping");
                continue;
            }

            DownloadedMediaTask dt = tasksByFilename.get(result.getOriginalFilename());
            if (dt == null) {
                log.warn("No task found for filename='{}', skipping", result.getOriginalFilename());
                continue;
            }

            // Compare against enum, not String
            if (BatchUploadResult.BatchFileResult.Status.SUCCESS == result.getStatus()
                    && result.getUrl() != null) {
                dt.getMediaTask().applyUrl(result.getUrl());
                log.debug("Mapped URL for file='{}' url='{}'", result.getOriginalFilename(), result.getUrl());
            } else {
                log.warn("File '{}' failed in storage service: {}",
                        result.getOriginalFilename(), result.getError());
            }
        }
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
