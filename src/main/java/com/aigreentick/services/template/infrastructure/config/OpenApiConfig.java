package com.aigreentick.services.template.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI auditLogOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Template  Service API")
                        .description("Template Management Service for  Apargo/Aigreentick modules ")
                        .version("v1")
                        .contact(new Contact().name("Apargo Platform Team")));
    }
}

