package com.aigreentick.services.template.infrastructure.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * Dedicated thread pool for media download/upload during Facebook template sync.
 *
 * Why a separate pool:
 *   - Isolates blocking I/O (HTTP downloads from Facebook) from the rest of the app
 *   - Prevents thread starvation of Reactor's boundedElastic or servlet threads
 *   - Fixed size = predictable concurrency, no shared resource contention
 *   - Integrates with Spring lifecycle for clean shutdown
 */
@Configuration
@Slf4j
public class MediaSyncThreadPoolConfig {

    private static final int POOL_SIZE = 15;
    private static final int QUEUE_CAPACITY = 100;
    private static final long KEEP_ALIVE_SECONDS = 60;

    @Bean(name = "mediaSyncExecutor", destroyMethod = "shutdown")
    public ExecutorService mediaSyncExecutor() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "media-sync-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                POOL_SIZE,                          // core pool size
                POOL_SIZE,                          // max pool size (fixed)
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()  // backpressure: caller thread runs the task
        );

        // Allow core threads to time out if idle — saves resources when no syncs are running
        executor.allowCoreThreadTimeOut(true);

        log.info("Media sync thread pool initialized: poolSize={}, queueCapacity={}",
                POOL_SIZE, QUEUE_CAPACITY);

        return executor;
    }
}