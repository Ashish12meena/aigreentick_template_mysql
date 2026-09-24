package com.aigreentick.services.template.infrastructure.config;

import com.aigreentick.services.template.common.constant.ApiPaths;
import com.aigreentick.services.template.infrastructure.idempotency.IdempotencyInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC wiring for cross-cutting API behaviour: the idempotency interceptor on
 * the public and internal surfaces (it acts only on {@code @Idempotent}
 * handlers).
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final IdempotencyInterceptor idempotencyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns(ApiPaths.API_V1 + "/**", ApiPaths.INTERNAL_V1 + "/**");
    }
}
