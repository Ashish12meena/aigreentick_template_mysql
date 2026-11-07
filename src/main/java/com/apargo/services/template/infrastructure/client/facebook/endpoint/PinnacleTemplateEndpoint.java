package com.apargo.services.template.infrastructure.client.facebook.endpoint;

import com.apargo.services.template.application.dto.client.AccessTokenIdentifier;
import com.apargo.services.template.common.constant.MetaGraph;
import com.apargo.services.template.infrastructure.config.properties.TemplateProviderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Pinnacle's partner API: {@code https://partnersv1.pinbot.ai/v3/{wabaId}/message_templates},
 * the customer's key in an {@code apikey} header — as the old system's {@code PinnacleApiHelper}
 * created, listed and deleted templates (doc 47 §1).
 *
 * <p>Pinnacle wrapped its media-upload answer in {@code {"response": {...}}}; {@link #unwrap}
 * accepts that shape for templates too, and Meta's plain shape.
 */
@Slf4j
@Component
public class PinnacleTemplateEndpoint implements TemplateEndpoint {

    static final String PROVIDER = "PINNACLE";
    static final String API_KEY_HEADER = "apikey";

    private final String baseUrl;
    private final String apiVersion;

    public PinnacleTemplateEndpoint(TemplateProviderProperties providers) {
        TemplateProviderProperties.Provider config = providers.find(PROVIDER)
                .orElseThrow(() -> new IllegalStateException(
                        "template.providers.pinnacle is missing: the Pinnacle template endpoint needs its base-url"));
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new IllegalStateException("template.providers.pinnacle.base-url is empty");
        }
        this.baseUrl = config.getBaseUrl();
        this.apiVersion = config.getApiVersion() == null || config.getApiVersion().isBlank() ? "v3" : config.getApiVersion();
        log.info("Pinnacle template endpoint enabled={} wabaAllowlist={} baseUrl={}/{}",
                config.isEnabled(), config.getWabaAllowlist() == null ? 0 : config.getWabaAllowlist().size(),
                baseUrl, apiVersion);
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String displayName() {
        return "Pinnacle";
    }

    @Override
    public UriComponentsBuilder templatesUri(String wabaId) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(apiVersion, wabaId, MetaGraph.Paths.MESSAGE_TEMPLATES);
    }

    @Override
    public void authenticate(HttpHeaders headers, AccessTokenIdentifier credential) {
        headers.set(API_KEY_HEADER, credential.getBspApiKey());
    }

    @Override
    public JsonNode unwrap(JsonNode response) {
        if (response != null && response.isObject() && !response.has("id")
                && response.has("response") && response.get("response").isObject()) {
            return response.get("response");
        }
        return response;
    }
}
