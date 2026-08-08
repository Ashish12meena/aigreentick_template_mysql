package com.aigreentick.services.template.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Wires {@link CorsProperties} into an actual CORS configuration.
 * <p>
 * {@code CorsProperties} being bound via {@code @ConfigurationProperties}
 * is not enough on its own, and neither is a lone {@link CorsConfigurationSource}
 * bean - this app has no Spring Security, and in plain Spring MVC a
 * {@code CorsConfigurationSource} bean is only auto-applied by Spring
 * Security's {@code http.cors()}. Without Security in the classpath, the
 * source bean sits in the context unused unless something explicitly
 * consumes it - hence the {@link CorsFilter} bean below, which registers
 * it directly with the servlet filter chain so every request (MVC or not)
 * actually gets CORS headers applied.
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final CorsProperties corsProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // setAllowedOriginPatterns supports "*" and wildcard subdomains
        // (e.g. "https://*.example.com") safely even when allowCredentials
        // is true - unlike setAllowedOrigins, which Spring rejects at
        // request time if allowCredentials=true and any entry is "*".
        configuration.setAllowedOriginPatterns(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setExposedHeaders(corsProperties.getExposedHeaders());
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(corsProperties.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public CorsFilter corsFilter(CorsConfigurationSource corsConfigurationSource) {
        return new CorsFilter(corsConfigurationSource);
    }
}