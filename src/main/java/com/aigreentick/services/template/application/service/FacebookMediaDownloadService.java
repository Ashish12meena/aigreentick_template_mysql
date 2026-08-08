package com.aigreentick.services.template.application.service;



import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles downloading media files from Facebook URLs to local temp files.
 * Pure application logic — no HTTP client dependencies, no Spring WebClient.
 * Uses standard Java URL I/O which is sufficient for downloading from
 * Facebook's CDN handles.
 *
 * Kept separate from MediaSyncService so the download concern can be
 * tested and reasoned about independently.
 */
@Service
@Slf4j
public class FacebookMediaDownloadService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Downloads a Facebook media handle URL to a local temp file.
     *
     * @param sourceUrl Facebook media handle URL
     * @param mediaType IMAGE, VIDEO, or DOCUMENT — determines file extension
     * @return temp file on disk, caller is responsible for deletion
     * @throws IOException if download fails
     */
    public File downloadToTempFile(String sourceUrl, String mediaType) throws IOException {
        String ext = resolveExtension(mediaType);
        String prefix = "media_sync_" + randomHex(8) + "_";
        File tempFile = File.createTempFile(prefix, ext);

        log.debug("Downloading media. url={} dest={}", sourceUrl, tempFile.getAbsolutePath());

        try (InputStream in = new URL(sourceUrl).openStream()) {
            Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        log.debug("Download complete. bytes={} file={}", tempFile.length(), tempFile.getName());
        return tempFile;
    }

    private String resolveExtension(String mediaType) {
        if (mediaType == null) return ".bin";
        return switch (mediaType.toUpperCase()) {
            case "VIDEO"    -> ".mp4";
            case "DOCUMENT" -> ".pdf";
            case "IMAGE"    -> ".jpg";
            default         -> ".bin";
        };
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        SECURE_RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(buf.length * 2);
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}