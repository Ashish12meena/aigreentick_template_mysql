package com.aigreentick.services.template.infrastructure.config.properties;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Connection settings for waba-service — bound to {@code waba-service.*}.
 *
 * <h2>Why the paths are a map</h2>
 *
 * A named map keeps the route out of Java while still failing loudly when a
 * key is missing, which is what {@link #path(String)} is for. The alternative
 * — a field per endpoint — means a code change every time a new call site
 * appears.
 *
 * <p>{@link #validate()} checks the required keys exist <em>at startup</em>.
 * Before this, a missing or misspelled key threw
 * {@link IllegalStateException} from {@link #path(String)} on the first
 * request that needed it, which surfaced as a {@code 500} on template create
 * in whichever environment had the typo — long after deployment.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "waba-service")
public class WabaServiceProperties {

    /** Base URL, e.g. {@code http://waba-service:8040}. */
    @NotBlank
    private String baseUrl;

    /** TCP connect timeout in milliseconds. */
    @Positive
    private int connectTimeout = 5000;

    /**
     * Socket read timeout in milliseconds. Credential resolution sits on the
     * template create/submit path, so this is deliberately short: a caller
     * waiting on a hung upstream is worse than a fast, clearly attributed
     * {@code 502}.
     */
    @Positive
    private int readTimeout = 10000;

    /** Named routes on waba-service. See {@link #path(String)}. */
    private Map<String, String> paths = new LinkedHashMap<>();

    /**
     * Route key for the WABA-scoped credential lookup. The value must point
     * at waba-service's internal surface,
     * {@code /internal/v1/waba-credentials/by-waba/{wabaId}}.
     */
    public static final String CREDENTIALS_BY_WABA = "credentials-by-waba";

    /**
     * Resolves a named path.
     *
     * @throws IllegalStateException if the key is absent — a typo in YAML must
     *                               not silently become a request to {@code null}
     */
    public String path(String key) {
        String value = paths.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing waba-service path config for key '" + key + "'. Known keys: " + paths.keySet());
        }
        return value;
    }

    @PostConstruct
    void validate() {
        // Touch every required key now so a misconfiguration fails the
        // deployment rather than the first template create that needs it.
        path(CREDENTIALS_BY_WABA);
    }
}
