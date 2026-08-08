package com.aigreentick.services.template.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "media-service")
@Data
public class MediaServiceProperties {
    private String baseUrl;

    private BatchConfig batch = new BatchConfig();
    private SingleConfig single = new SingleConfig();

    @Data
    public static class BatchConfig {
        private long maxBytes = 83_886_080L;
        private int maxFiles = 20;
        private String uploadPath = "/api/v1/media/upload/batch";
    }

    @Data
    public static class SingleConfig {
        private String uploadPath = "/api/v1/media/upload";
    }
}