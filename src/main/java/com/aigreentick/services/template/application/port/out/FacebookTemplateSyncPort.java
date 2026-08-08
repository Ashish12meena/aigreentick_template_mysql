package com.aigreentick.services.template.application.port.out;

import java.util.Optional;

import com.aigreentick.services.template.api.dto.response.client.FacebookApiResponse;
import com.fasterxml.jackson.databind.JsonNode;

public interface FacebookTemplateSyncPort {

    FacebookApiResponse<JsonNode> getAllTemplates(
            String wabaId,
            String accessToken,
            Optional<String> status,
            Optional<String> language,
            Optional<String> category,
            Optional<String> name,
            Optional<Integer> limit,
            Optional<String> after
    );
}