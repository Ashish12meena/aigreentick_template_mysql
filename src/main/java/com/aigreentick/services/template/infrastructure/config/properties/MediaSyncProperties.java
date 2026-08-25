package com.aigreentick.services.template.infrastructure.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Thread pool sizing for media download/upload during template sync —
 * bound to {@code media-sync.*}.
 *
 * <p>These were three {@code private static final int} constants on
 * {@code MediaSyncThreadPoolConfig}. Pool sizing is the definition of an
 * environment-specific value: the right number on a developer laptop and the
 * right number on a production node are not the same, and baking it into a
 * class means the only way to change it is a rebuild and a redeploy.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "media-sync")
public class MediaSyncProperties {

    /**
     * Fixed pool size. This is blocking HTTP I/O against Meta, so the useful
     * ceiling is set by Meta's rate limits and by how many concurrent syncs
     * the node should tolerate — not by CPU count.
     */
    @Positive
    private int poolSize = 15;

    /**
     * Bounded queue depth. Bounded on purpose: an unbounded queue converts
     * back-pressure into heap growth, and the failure mode becomes an
     * out-of-memory kill instead of a slow sync.
     */
    @Positive
    private int queueCapacity = 100;

    /** Idle thread keep-alive, seconds. */
    @Positive
    private long keepAliveSeconds = 60;

    /** Grace period for in-flight tasks on shutdown, seconds. */
    @Positive
    private long awaitTerminationSeconds = 30;

    /** Thread name prefix, so a stack dump attributes threads to this pool. */
    @NotBlank
    private String threadNamePrefix = "media-sync-";
}
