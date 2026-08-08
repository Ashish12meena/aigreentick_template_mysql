package com.aigreentick.services.template.application.service;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.application.dto.TenantScope;
import com.aigreentick.services.template.application.dto.result.TemplateResult;
import com.aigreentick.services.template.api.dto.response.client.AccessTokenIdentifier;
import com.aigreentick.services.template.api.dto.response.client.FacebookApiResponse;
import com.aigreentick.services.template.application.mapper.WhatsappTemplateMapper;
import com.aigreentick.services.template.application.port.out.FacebookTemplatePort;
import com.aigreentick.services.template.application.port.out.WabaCredentialPort;
import com.aigreentick.services.template.common.exception.WhatsappCredentialsNotFoundException;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.service.TemplateCommandService;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared logic for submitting a template to Meta (Facebook).
 * Used by both CreateTemplateUseCaseImpl and SubmitDraftToMetaUseCaseImpl
 * to avoid code duplication.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MetaTemplateSubmissionService {

    private final TemplateCommandService commandService;
    private final WhatsappTemplateMapper templateMapper;
    // private final WabaCredentialAdapter accountClient;
    private final WabaCredentialPort wabaCredentialPort;
    private final FacebookTemplatePort ftp;

    /**
     * Submits a template to Facebook Graph API.
     *
     * Flow:
     * 1. Mark template as SUBMITTED
     * 2. Resolve WABA access token
     * 3. Call Facebook API
     * 4. Handle success/failure and update template accordingly
     *
     * @param template the saved template entity
     * @param payload  serialized JSON body for Facebook API
     * @param wabaId   WhatsApp Business Account ID
     * @return response DTO with success or error details
     */
    public TemplateResult submitToMeta(WhatsappTemplate template, String payload, String wabaId) {

        // Step 1: Mark as in-flight
        commandService.markAsSubmitted(template);
        log.info("Template status → SUBMITTED, templateId={}", template.getId());

        // Step 2: Resolve access token
        AccessTokenIdentifier credentials = wabaCredentialPort
                .getWhatsappAccountWabaAccessToken(wabaId, TenantScope.of(template));
        if (credentials == null || credentials.getAccessToken().isBlank()) {
            throw new WhatsappCredentialsNotFoundException(
                    "Access token not found for wabaId: " + wabaId);
        }

        // Step 3: Call Facebook
        FacebookApiResponse<JsonNode> fbResponse = callFacebookApi(template, payload, wabaId, credentials);
        if (fbResponse == null) {
            return buildErrorResponse(template, "Facebook API call failed");
        }

        // Step 4: Handle HTTP-level failure
        if (!fbResponse.isSuccess()) {
            log.warn("Facebook returned error for templateId={}: {}",
                    template.getId(), fbResponse.getErrorMessage());
            commandService.markAsFailed(template, fbResponse.getErrorMessage(), null);
            return buildErrorResponse(template, fbResponse.getErrorMessage());
        }

        // commandService.markAsSubmitted(template);

        // return TemplateResult.builder()
        // .id(template)
        // .build();

        return handleFacebookResponse(template, fbResponse.getData());
    }

    // ── Internal helpers ──

    private FacebookApiResponse<JsonNode> callFacebookApi(
            WhatsappTemplate template, String payload, String wabaId, AccessTokenIdentifier credentials) {
        try {
            return ftp.createTemplate(payload, wabaId, credentials.getAccessToken());
        } catch (Exception e) {
            log.error("Facebook API call failed for templateId={}", template.getId(), e);
            commandService.markAsFailed(template,
                    "Facebook API call failed: " + e.getMessage(), null);
            return null;
        }
    }

    private TemplateResult handleFacebookResponse(WhatsappTemplate template, JsonNode jsonData) {
        String metaResponse = jsonData.toString();

        // Check for error in response body
        if (jsonData.has("error")) {
            String errorMsg = jsonData.path("error").path("message").asText("Unknown error");
            log.warn("Facebook error in response for templateId={}: {}", template.getId(), errorMsg);
            commandService.markAsFailed(template, errorMsg, metaResponse);
            return TemplateResult.builder()
                    .id(template.getId())
                    .name(template.getName())
                    .errorMessage(errorMsg)
                    .errorPayload(jsonData)
                    .build();
        }

        // Extract success fields
        String metaTemplateId = jsonData.path("id").asText(null);
        String status = jsonData.path("status").asText(null);
        String category = jsonData.path("category").asText(null);

        if (metaTemplateId == null || status == null) {
            log.warn("Invalid Facebook response for templateId={}", template.getId());
            commandService.markAsFailed(template, "Invalid response from Facebook API", metaResponse);
            return TemplateResult.builder()
                    .id(template.getId())
                    .name(template.getName())
                    .errorMessage("Invalid response from Facebook API")
                    .errorPayload(jsonData)
                    .build();
        }

        // Success
        commandService.markAsNewCreated(template, metaTemplateId, status, metaResponse);
        log.info("Template submitted to Meta: templateId={} metaId={} status={}",
                template.getId(), metaTemplateId, status);

        return templateMapper.mapToTemplateResponse(template, metaTemplateId, status, category);
    }

    private TemplateResult buildErrorResponse(WhatsappTemplate template, String message) {
        return TemplateResult.builder()
                .id(template.getId())
                .name(template.getName())
                .status(template.getStatus())
                .errorMessage(message)
                .build();
    }

}