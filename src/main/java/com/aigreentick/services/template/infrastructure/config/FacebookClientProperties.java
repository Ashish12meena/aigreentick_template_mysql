package com.aigreentick.services.template.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "facebook-service")
@Data
public class FacebookClientProperties {
    private String baseUrl;
    private String apiVersion;

}
