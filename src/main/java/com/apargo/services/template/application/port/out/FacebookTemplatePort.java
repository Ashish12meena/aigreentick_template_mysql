package com.apargo.services.template.application.port.out;


import com.apargo.services.template.application.dto.client.FacebookApiResponse;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Outbound port for WhatsApp template lifecycle operations against Facebook.
 * Use cases depend on this interface — never on FacebookGraphApiClient directly.
 */
public interface FacebookTemplatePort {

    /**
     * Submits a template to Facebook for approval.
     *
     * @param bodyJson     serialized JSON payload (snake_case, Facebook format)
     * @param wabaId       WhatsApp Business Account ID
     * @param accessToken  WABA-scoped access token
     * @return raw Facebook response wrapped in a result type
     */
    FacebookApiResponse<JsonNode> createTemplate(String bodyJson, String wabaId, String accessToken);

    /**
     * Edits an existing template ({@code POST /{meta-template-id}}). Meta puts
     * it back under review; the response is {@code {"success": true}}.
     *
     * @param bodyJson {@code components}, plus {@code category} when allowed
     */
    FacebookApiResponse<JsonNode> editTemplate(String metaTemplateId, String bodyJson, String accessToken);

    /**
     * Deletes one template (one language) from Facebook.
     * Meta needs both values: by name alone it deletes ALL languages of that
     * name, so {@code metaTemplateId} ({@code hsm_id}) narrows it to this one.
     *
     * @param templateName   the template name
     * @param metaTemplateId Meta's template id for this language
     * @param wabaId         WhatsApp Business Account ID
     * @param accessToken    WABA-scoped access token
     */
    FacebookApiResponse<JsonNode> deleteTemplate(String templateName, String metaTemplateId,
            String wabaId, String accessToken);
}