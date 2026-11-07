package com.apargo.services.template.infrastructure.client.facebook.endpoint;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The {@link TemplateEndpoint} strategies, keyed by provider code. Two endpoints for one provider
 * fail the application at startup.
 */
@Slf4j
@Component
public class TemplateEndpointRegistry {

    private final Map<String, TemplateEndpoint> byProvider;

    public TemplateEndpointRegistry(List<TemplateEndpoint> endpoints) {
        Map<String, TemplateEndpoint> map = new LinkedHashMap<>();
        for (TemplateEndpoint endpoint : endpoints) {
            TemplateEndpoint previous = map.put(key(endpoint.provider()), endpoint);
            if (previous != null) {
                throw new IllegalStateException("Two template endpoints for provider " + endpoint.provider()
                        + ": " + previous.getClass().getName() + " and " + endpoint.getClass().getName());
            }
        }
        this.byProvider = Collections.unmodifiableMap(map);
        log.info("Template endpoints registered for providers {}", byProvider.keySet());
    }

    public Optional<TemplateEndpoint> find(String provider) {
        return provider == null ? Optional.empty() : Optional.ofNullable(byProvider.get(key(provider)));
    }

    public boolean has(String provider) {
        return find(provider).isPresent();
    }

    private static String key(String provider) {
        return provider.toUpperCase(Locale.ROOT);
    }
}
