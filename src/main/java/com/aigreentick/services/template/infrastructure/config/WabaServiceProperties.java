package com.aigreentick.services.template.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "waba-service")
@Data
public class WabaServiceProperties {

    private String baseUrl;
    private int connectTimeout = 5000;
    private int readTimeout = 10000;
    private Map<String, String> paths;

    /**
     * Resolves a named path from the paths map.
     * e.g. path("get-credentials") → "/internal/waba-accounts/{wabaAccountId}/credentials"
     */
    public String path(String key) {
        if (paths == null || !paths.containsKey(key)) {
            throw new IllegalStateException("Missing WABA service path config for key: " + key);
        }
        return paths.get(key);
    }
}