package com.apargo.services.template.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * BSPs that template create and delete can be routed through — {@code template.providers.*}
 * (doc 47 R4). Meta is {@code facebook-service} and is always available.
 *
 * <pre>
 * template.providers.pinnacle:
 *   enabled: false            # off = Pinnacle WABAs' templates go through Meta, as before
 *   waba-allowlist: []        # Meta WABA ids; empty = every Pinnacle WABA (once enabled)
 *   base-url: https://partnersv1.pinbot.ai
 *   api-version: v3
 * </pre>
 *
 * <p>The map key is the provider code in lower case. A new BSP is a block here plus one
 * {@code TemplateEndpoint} class.
 */
@Data
@ConfigurationProperties(prefix = "template")
public class TemplateProviderProperties {

    private Map<String, Provider> providers = new LinkedHashMap<>();

    /** The block for this provider code, matched case-insensitively. */
    public Optional<Provider> find(String providerCode) {
        if (providerCode == null || providers == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(providerCode.toLowerCase(Locale.ROOT)));
    }

    @Data
    public static class Provider {

        /** Whether this provider's WABAs create and delete templates through it at all. */
        private boolean enabled = false;

        /** When not empty, only these Meta WABA ids are routed through the provider (canary). */
        private List<String> wabaAllowlist = new ArrayList<>();

        /** Host of the provider's Cloud-API-compatible endpoints, no path, no trailing slash. */
        private String baseUrl;

        /** The provider's path version segment ({@code v3} for Pinnacle). */
        private String apiVersion;

        /** Switch on, and the allowlist (if any) admits this WABA. */
        public boolean routes(String wabaId) {
            if (!enabled) {
                return false;
            }
            List<String> allowlist = wabaAllowlist == null ? List.of()
                    : wabaAllowlist.stream().filter(id -> id != null && !id.isBlank()).map(String::trim).toList();
            return allowlist.isEmpty() || allowlist.contains(wabaId);
        }
    }
}
