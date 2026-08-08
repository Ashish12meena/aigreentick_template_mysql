package com.aigreentick.services.template.api.dto.response;

/**
 * Statistics returned after syncing templates with Facebook.
 *
 * <p>For async syncs, {@link #started()} returns a sentinel where all counts are
 * {@code -1}, indicating the sync has been accepted but not yet completed.
 * The real results are written to the application logs.
 *
 * @param inserted Number of new templates inserted
 * @param updated  Number of existing templates updated with Facebook data
 * @param deleted  Number of stale templates soft-deleted
 */
public record TemplateSyncStats(int inserted, int updated, int deleted) {

    /**
     * Sentinel returned immediately from the async sync endpoint.
     * All counts are {@code -1} — the sync is running in the background.
     */
    public static TemplateSyncStats started() {
        return new TemplateSyncStats(-1, -1, -1);
    }

    /** @return true if this is an async "accepted" marker rather than real results */
    public boolean isStarted() {
        return inserted == -1 && updated == -1 && deleted == -1;
    }

    /**
     * Legacy two-arg constructor (inserted includes both new and updated).
     */
    public TemplateSyncStats(int insertedOrUpdated, int deleted) {
        this(insertedOrUpdated, 0, deleted);
    }

    /** Total templates processed (inserted + updated). Only meaningful when {@link #isStarted()} is false. */
    public int totalProcessed() {
        return inserted + updated;
    }
}