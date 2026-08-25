package com.aigreentick.services.template.infrastructure.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for storage-service — bound to {@code media-service.*}.
 *
 * <h2>These paths are a cross-service contract</h2>
 *
 * {@link Batch#uploadPath} and {@link Single#uploadPath} must match the
 * routes storage-service actually publishes
 * ({@code /api/v1/media/upload/batch} and {@code /api/v1/media/upload}).
 * They are configurable so a gateway prefix can be added without a code
 * change, not so they can be pointed somewhere arbitrary.
 *
 * <h2>Why the batch limits are duplicated here</h2>
 *
 * storage-service enforces its own {@code batch-max-files} and
 * {@code batch-max-total-size} and will reject an oversized batch. Holding
 * the same ceilings on this side lets the sync flow split a large batch
 * <em>before</em> spending a round trip and an upload on something that is
 * going to be refused. The two must be kept in step; if they drift, the
 * lower of the two wins and the only symptom is an avoidable rejection.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "media-service")
public class MediaServiceProperties {

    /** Base URL, e.g. {@code http://storage-service:7998}. */
    @NotBlank
    private String baseUrl;

    /** TCP connect timeout in milliseconds. */
    @Positive
    private int connectTimeout = 5000;

    /**
     * Socket read timeout in milliseconds. Deliberately far longer than the
     * waba-service timeout: this call carries file bytes, and a batch of
     * twenty images is not comparable to a credential lookup.
     */
    @Positive
    private int readTimeout = 60000;

    /**
     * Maximum bytes buffered when decoding a storage-service response.
     * WebClient's 256 KB default is enough for the JSON envelope returned by
     * a batch upload, but leaves no headroom if that response ever grows.
     */
    @Positive
    private int maxInMemorySizeBytes = 2 * 1024 * 1024;

    @Valid
    private Batch batch = new Batch();

    @Valid
    private Single single = new Single();

    @Data
    public static class Batch {

        /** Route on storage-service. Must match its published path. */
        @NotBlank
        private String uploadPath = "/api/v1/media/upload/batch";

        /** Aggregate ceiling per batch. Mirrors storage-service. */
        @Positive
        private long maxBytes = 83_886_080L;

        /** File-count ceiling per batch. Mirrors storage-service. */
        @Positive
        private int maxFiles = 20;
    }

    @Data
    public static class Single {

        /** Route on storage-service. Must match its published path. */
        @NotBlank
        private String uploadPath = "/api/v1/media/upload";
    }
}
