package com.aigreentick.services.template.infrastructure.config;

import com.aigreentick.services.template.infrastructure.config.properties.MediaSyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated thread pool for media download/upload during template sync.
 *
 * <h2>Why a separate pool</h2>
 *
 * <ul>
 *   <li>Isolates blocking HTTP I/O against Meta from servlet request threads
 *       and from Reactor's {@code boundedElastic} scheduler. A slow Meta
 *       response should delay a sync, not stall unrelated requests.</li>
 *   <li>Fixed size gives predictable concurrency against Meta's rate
 *       limits.</li>
 *   <li>Named threads make a stack dump during a slow sync legible.</li>
 * </ul>
 *
 * <h2>What changed</h2>
 *
 * The pool was a raw {@link ThreadPoolExecutor} with three hardcoded
 * constants, registered with {@code destroyMethod = "shutdown"}.
 * {@code shutdown()} returns immediately without waiting, so a redeploy
 * could tear the JVM down with half-uploaded media still in flight —
 * leaving templates pointing at URLs that were never written.
 *
 * <p>{@link ThreadPoolTaskExecutor} is Spring's own wrapper: it participates
 * in the context lifecycle and, with
 * {@code setWaitForTasksToCompleteOnShutdown(true)}, drains in-flight tasks
 * up to a bounded grace period. Sizing now comes from
 * {@link MediaSyncProperties}.
 *
 * <p>{@link ThreadPoolExecutor.CallerRunsPolicy} is retained deliberately.
 * When the bounded queue is full the submitting thread runs the task itself,
 * which throttles the producer instead of discarding work — for a sync,
 * slower is correct and silently dropping media is not.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MediaSyncThreadPoolConfig {

    /** Bean name referenced by {@code @Qualifier} at the injection sites. */
    public static final String MEDIA_SYNC_EXECUTOR = "mediaSyncExecutor";

    private final MediaSyncProperties properties;

    @Bean(name = MEDIA_SYNC_EXECUTOR)
    public ThreadPoolTaskExecutor mediaSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getPoolSize());
        executor.setMaxPoolSize(properties.getPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds((int) properties.getKeepAliveSeconds());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds((int) properties.getAwaitTerminationSeconds());
        executor.initialize();

        log.info("Media sync thread pool initialised: poolSize={} queueCapacity={} awaitTerminationSeconds={}",
                properties.getPoolSize(), properties.getQueueCapacity(),
                properties.getAwaitTerminationSeconds());

        return executor;
    }
}
