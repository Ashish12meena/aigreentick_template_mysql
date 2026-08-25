package com.aigreentick.services.template.infrastructure.config;

import com.aigreentick.services.template.common.constant.InternalHeaders;
import com.aigreentick.services.template.common.constant.SecuritySchemes;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger wiring.
 *
 * <p>Which paths appear is controlled entirely by
 * {@code springdoc.paths-to-match} in YAML — nothing here filters anything.
 * {@code application-prod.yml} restricts it to {@code /api/**} so the
 * internal endpoints are not published; dev leaves them visible.
 *
 * <p>That is documentation hygiene, not security. The gateway deny rule on
 * {@code /internal/**} and {@code InternalApiAuthFilter} are what keep
 * outsiders out — an endpoint missing from Swagger still answers.
 *
 * <p>The bean was previously named {@code auditLogOpenAPI}, which is a
 * leftover from a different service entirely.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI templateOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Template Service API")
                        .description("WhatsApp message template management for Apargo/Aigreentick modules. "
                                + "Note: all request and response bodies use snake_case field names.")
                        .version("v1")
                        .contact(new Contact().name("Apargo Platform Team")))
                .components(new Components()
                        .addSecuritySchemes(SecuritySchemes.INTERNAL_API_KEY, internalApiKeyScheme()));
    }

    /**
     * Declares {@code X-Internal-Api-Key} so Swagger UI renders an Authorize
     * button. The key is checked by a servlet filter rather than bound as a
     * controller argument, so springdoc cannot infer it — without this,
     * "Try it out" against an internal endpoint returns 401 with no hint why.
     */
    private SecurityScheme internalApiKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(InternalHeaders.API_KEY)
                .description("Value of INTERNAL_API_KEY on the server.");
    }
}
