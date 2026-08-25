package com.aigreentick.services.template.infrastructure.config.properties;

import com.aigreentick.services.template.common.constant.ApiPaths;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The service-to-service surface — bound to {@code internal.api.*}.
 *
 * <p>Mirrors {@code InternalApiProperties} in waba-service so an operator
 * configuring the platform sets the same keys with the same meanings
 * everywhere.
 */
@Slf4j
@Data
@Validated
@ConfigurationProperties(prefix = "internal.api")
public class InternalApiProperties {

    /**
     * Path prefix the auth filter guards. Cross-checked against
     * {@link ApiPaths#INTERNAL} at startup — the two are edited in different
     * files, and a mismatch would silently leave the internal surface
     * unguarded rather than fail.
     */
    private String pathPrefix = ApiPaths.INTERNAL;

    /**
     * Shared secret this service both requires from inbound internal callers
     * and presents on outbound calls to waba-service and storage-service.
     *
     * <p>There is no default. A blank key would either lock out every caller
     * or, worse, be silently accepted — failing at startup is the only
     * honest option.
     */
    @NotBlank
    private String apiKey;

    /**
     * Defaults to true. Setting this false leaves the internal surface
     * unauthenticated — local development only.
     */
    private boolean authEnabled = true;

    /** Minimum key length. Short keys are brute-forceable offline. */
    private static final int MIN_KEY_LENGTH = 32;

    @PostConstruct
    void validate() {
        if (!ApiPaths.INTERNAL.equals(pathPrefix)) {
            throw new IllegalStateException(
                    "internal.api.path-prefix is '" + pathPrefix + "' but ApiPaths.INTERNAL is '"
                            + ApiPaths.INTERNAL + "'. The auth filter would guard a different prefix "
                            + "than the controllers are mapped to, leaving them unauthenticated.");
        }
        if (apiKey.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "internal.api.api-key must be at least " + MIN_KEY_LENGTH
                            + " characters; got " + apiKey.length() + ".");
        }
        if (!authEnabled) {
            log.warn("internal.api.auth-enabled=false - {}/** is UNAUTHENTICATED. "
                    + "This must never be the case outside local development.", pathPrefix);
        }
    }
}
