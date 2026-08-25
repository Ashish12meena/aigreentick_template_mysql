package com.aigreentick.services.template.application.port.out;


import com.aigreentick.services.template.application.dto.client.FacebookApiResponse;
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
     * Deletes a template from Facebook by name.
     * Facebook deletes ALL languages for the given name in one call.
     *
     * @param templateName the template name (not the meta template ID)
     * @param wabaId       WhatsApp Business Account ID
     * @param accessToken  WABA-scoped access token
     */
    FacebookApiResponse<JsonNode> deleteTemplate(String templateName, String wabaId, String accessToken);
}