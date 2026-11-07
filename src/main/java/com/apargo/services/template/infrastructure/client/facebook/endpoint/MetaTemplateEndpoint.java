package com.apargo.services.template.infrastructure.client.facebook.endpoint;

import com.apargo.services.template.application.dto.client.AccessTokenIdentifier;
import com.apargo.services.template.common.constant.MetaGraph;
import com.apargo.services.template.infrastructure.config.properties.FacebookClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Meta Graph ({@code facebook-service}), bearer token. Every WABA without a BSP route. */
@Component
@RequiredArgsConstructor
public class MetaTemplateEndpoint implements TemplateEndpoint {

    private final FacebookClientProperties properties;

    @Override
    public String provider() {
        return AccessTokenIdentifier.META_DIRECT;
    }

    @Override
    public String displayName() {
        return "Meta";
    }

    @Override
    public UriComponentsBuilder templatesUri(String wabaId) {
        return UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .pathSegment(properties.getApiVersion(), wabaId, MetaGraph.Paths.MESSAGE_TEMPLATES);
    }

    @Override
    public void authenticate(HttpHeaders headers, AccessTokenIdentifier credential) {
        headers.setBearerAuth(credential.getAccessToken());
    }
}
