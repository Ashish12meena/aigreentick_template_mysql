package com.apargo.services.template.infrastructure.client.account;

import com.apargo.services.template.application.dto.client.AccessTokenIdentifier;
import com.apargo.services.template.infrastructure.client.facebook.endpoint.TemplateEndpointRegistry;
import com.apargo.services.template.infrastructure.config.properties.TemplateProviderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Turns waba-service's credential answer into the route for template create and delete
 * (doc 47 R4):
 *
 * <pre>
 * provider META_DIRECT (or absent)                       → Meta
 * provider X, template.providers.x off or not listed     → Meta   (today's behaviour)
 * provider X, on, allowlist set and WABA not on it       → Meta   (canary)
 * provider X, on, no endpoint registered for X           → X, unavailable
 * provider X, on, waba returned no usable key            → X, unavailable (waba's reason)
 * provider X, on, key present                            → X
 * </pre>
 *
 * <p>waba-service decides the provider; this only applies the switch. An enabled BSP without a key
 * stays a BSP route and the create or delete fails visibly — it never quietly goes to Meta.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateRoutePolicy {

    private final TemplateProviderProperties providers;
    private final TemplateEndpointRegistry endpoints;

    public AccessTokenIdentifier route(String wabaId, WabaCredentialsResponse answer) {
        String provider = answer.getProvider();
        String token = answer.getAccessToken();
        if (provider == null || provider.isBlank() || AccessTokenIdentifier.META_DIRECT.equalsIgnoreCase(provider)) {
            return new AccessTokenIdentifier(token);
        }

        TemplateProviderProperties.Provider config = providers.find(provider).orElse(null);
        if (config == null || !config.routes(wabaId)) {
            log.debug("Template provider {} not routed for wabaId={} (enabled={}); using Meta",
                    provider, wabaId, config != null && config.isEnabled());
            return new AccessTokenIdentifier(token);
        }
        if (!endpoints.has(provider)) {
            log.warn("Template provider {} is enabled but has no endpoint; wabaId={}", provider, wabaId);
            return AccessTokenIdentifier.bspUnavailable(provider, token,
                    "No template endpoint is registered for provider " + provider);
        }

        WabaCredentialsResponse.BspPart bsp = answer.getBsp();
        if (bsp == null || bsp.getApiKey() == null || bsp.getApiKey().isBlank()) {
            log.warn("No usable {} credential for templates on wabaId={}: {}",
                    provider, wabaId, answer.getBspUnavailableReason());
            return AccessTokenIdentifier.bspUnavailable(provider, token, answer.getBspUnavailableReason());
        }
        return AccessTokenIdentifier.bsp(provider, token, bsp.getApiKey());
    }
}
