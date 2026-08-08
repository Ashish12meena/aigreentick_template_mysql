package com.aigreentick.services.template.application.service;

import java.io.File;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Holds a downloaded temp file paired with its original MediaTask.
 * Created during the parallel download phase, consumed during batch upload.
 */
@Getter
@AllArgsConstructor
public class DownloadedMediaTask {
    private final MediaSyncService.MediaTask mediaTask;
    private final File tempFile;
    private final int taskIndex;
    private final String uploadFilename; // deterministic: "{index}_{mediaType}.{ext}"
}