package com.aigreentick.services.template.infrastructure.config;

import com.aigreentick.services.template.common.constant.ApiHeaders;
import com.aigreentick.services.template.common.constant.InternalHeaders;
import com.aigreentick.services.template.common.constant.SecuritySchemes;
import com.aigreentick.services.template.common.web.Idempotent;
import com.aigreentick.services.template.infrastructure.config.properties.IdempotencyProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
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
                                + "Follows the company API Standard: camelCase JSON, ISO-8601 UTC times, "
                                + "every JSON response wrapped as {success, status, code, message, data, errors, meta}, "
                                + "lists as data = {items, pagination}.")
                        .version("v1")
                        .contact(new Contact().name("Apargo Platform Team")))
                .components(new Components()
                        .addSecuritySchemes(SecuritySchemes.INTERNAL_API_KEY, internalApiKeyScheme()));
    }

    /**
     * Documents the headers every endpoint accepts but no controller binds as
     * a parameter: {@code X-Request-Id} and {@code X-User-Id} are read by
     * {@code RequestIdFilter}, and {@code X-Idempotency-Key} by the
     * idempotency interceptor on {@link Idempotent} endpoints.
     */
    @Bean
    public OperationCustomizer standardHeadersCustomizer(IdempotencyProperties idempotency) {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(header(ApiHeaders.REQUEST_ID, false,
                    "Tracks one request across services; generated and echoed if absent"));
            operation.addParametersItem(header(ApiHeaders.USER_ID, false,
                    "User performing the action, when a user is acting"));
            if (handlerMethod.hasMethodAnnotation(Idempotent.class)) {
                operation.addParametersItem(header(ApiHeaders.IDEMPOTENCY_KEY, idempotency.isRequired(),
                        "Unique per logical operation (a UUID). A repeat with the same key returns the "
                                + "first response instead of running again."));
            }
            return operation;
        };
    }

    private static HeaderParameter header(String name, boolean required, String description) {
        HeaderParameter parameter = new HeaderParameter();
        parameter.setName(name);
        parameter.setRequired(required);
        parameter.setDescription(description);
        parameter.setSchema(new StringSchema());
        return parameter;
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
