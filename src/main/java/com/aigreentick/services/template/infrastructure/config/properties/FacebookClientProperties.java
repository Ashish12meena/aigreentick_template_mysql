package com.aigreentick.services.template.infrastructure.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Meta Graph API settings — bound to {@code facebook-service.*}.
 *
 * <p>The version is pinned rather than tracking "latest" on purpose: Meta
 * changes template payload shapes between Graph versions, and an unpinned
 * client would start failing on a date nobody chose.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "facebook-service")
public class FacebookClientProperties {

    /** Graph API host, e.g. {@code https://graph.facebook.com}. */
    @NotBlank
    private String baseUrl;

    /** Graph API version, e.g. {@code v23.0}. */
    @NotBlank
    @Pattern(regexp = "v\\d+\\.\\d+",
            message = "must be a Graph API version such as 'v23.0'")
    private String apiVersion;

    /** TCP connect timeout in milliseconds. */
    @Positive
    private int connectTimeout = 5000;

    /** Socket read timeout in milliseconds. */
    @Positive
    private int readTimeout = 30000;

    /**
     * Maximum bytes buffered when decoding a Graph API response. A WABA with
     * several hundred templates returns a sync payload far larger than
     * WebClient's 256 KB default, which fails as an opaque
     * {@code DataBufferLimitException} rather than as anything resembling
     * "the response was too big".
     */
    @Positive
    private int maxInMemorySizeBytes = 16 * 1024 * 1024;

    /** Convenience: {@code {baseUrl}/{apiVersion}}. */
    public String versionedBaseUrl() {
        return baseUrl + "/" + apiVersion;
    }
}
