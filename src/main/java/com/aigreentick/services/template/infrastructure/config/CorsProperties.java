package com.aigreentick.services.template.infrastructure.config;


import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) configuration.
 * <p>
 * Bound from the {@code cors:} block in {@code application.yaml} — no
 * origin, method, or header is ever hardcoded in Java. This is what a
 * frontend (running on a different origin — different scheme, host, or
 * port than this API) needs the server to explicitly allow before the
 * browser will let its JavaScript read the response, per the
 * same-origin policy.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    /**
     * Origins allowed to call this API, e.g. {@code https://app.example.com}.
     * Supports wildcard patterns (e.g. {@code *} or {@code https://*.example.com})
     * because {@link CorsConfig} applies these via
     * {@code CorsConfiguration#setAllowedOriginPatterns}, which — unlike
     * {@code setAllowedOrigins} — safely reflects the matched origin back
     * even when {@code allow-credentials: true}. Still, prefer listing
     * explicit origins in production rather than relying on {@code *}.
     */
    @NotEmpty
    private List<String> allowedOrigins = new ArrayList<>();

    /** HTTP methods the frontend is allowed to use. */
    private List<String> allowedMethods = new ArrayList<>(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

    /** Request headers the frontend is allowed to send. */
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

    /** Response headers exposed to frontend JavaScript beyond the CORS-safelisted defaults. */
    private List<String> exposedHeaders = new ArrayList<>();

    /** Whether cookies/Authorization headers are allowed on cross-origin requests. */
    private boolean allowCredentials = false;

    /** How long (seconds) the browser may cache a preflight OPTIONS response. */
    private long maxAgeSeconds = 3600;
}