package com.aigreentick.services.template.common.constant;

/**
 * OpenAPI security scheme names.
 *
 * <p>The name is referenced in two places that must match exactly — the
 * {@code @SecurityScheme} definition in {@code OpenApiConfig} and every
 * {@code @SecurityRequirement} on a controller. A typo in either produces a
 * Swagger page that renders without complaint and simply omits the auth
 * field, so the mismatch is invisible until someone tries to use it.
 */
public final class SecuritySchemes {

    /** Shared-secret header guarding {@code /internal/**}. */
    public static final String INTERNAL_API_KEY = "InternalApiKey";

    private SecuritySchemes() {
    }
}
