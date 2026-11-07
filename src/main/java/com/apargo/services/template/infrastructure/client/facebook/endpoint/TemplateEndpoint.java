package com.apargo.services.template.infrastructure.client.facebook.endpoint;

import com.apargo.services.template.application.dto.client.AccessTokenIdentifier;
import com.apargo.services.template.common.constant.MetaGraph;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * What differs between the providers a template can be created and deleted through — one
 * implementation per provider (strategy), picked by {@link TemplateEndpointRegistry} from the
 * credential's route (doc 47 R4).
 *
 * <p>The calls themselves (body, error contract, logging) are shared in
 * {@code FacebookTemplateAdapter}: the providers we support mirror Meta's
 * {@code /{wabaId}/message_templates}. Adding a provider is one class plus a
 * {@code template.providers.<code>} block.
 */
public interface TemplateEndpoint {

    /** The provider code it serves ({@code META_DIRECT}, {@code PINNACLE}). */
    String provider();

    /** For logs and messages: "Meta", "Pinnacle". */
    String displayName();

    /** {@code <host>/<version>/{wabaId}/message_templates}, with no query. */
    UriComponentsBuilder templatesUri(String wabaId);

    /** Sets this provider's authentication. Never logs the secret. */
    void authenticate(HttpHeaders headers, AccessTokenIdentifier credential);

    /**
     * The query that deletes one template. Meta needs both: by name alone it deletes every language
     * of that name, so {@code hsm_id} narrows it to this one.
     */
    default UriComponentsBuilder deleteQuery(UriComponentsBuilder uri, String templateName, String metaTemplateId) {
        uri.queryParam(MetaGraph.Params.NAME, templateName);
        if (metaTemplateId != null && !metaTemplateId.isBlank()) {
            uri.queryParam(MetaGraph.Params.HSM_ID, metaTemplateId);
        }
        return uri;
    }

    /** The response in Meta's shape. Identity for Meta. */
    default JsonNode unwrap(JsonNode response) {
        return response;
    }
}
