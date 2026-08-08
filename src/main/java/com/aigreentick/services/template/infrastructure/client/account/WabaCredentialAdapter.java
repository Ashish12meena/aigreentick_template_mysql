package com.aigreentick.services.template.infrastructure.client.account;

import com.aigreentick.services.template.api.constants.ApiHeaders;
import com.aigreentick.services.template.api.dto.response.client.AccessTokenIdentifier;
import com.aigreentick.services.template.api.dto.response.client.WhatsappAccountCredentials;
import com.aigreentick.services.template.application.dto.TenantScope;
import com.aigreentick.services.template.application.port.out.WabaCredentialPort;
import com.aigreentick.services.template.common.exception.WhatsappCredentialsNotFoundException;
import com.aigreentick.services.template.infrastructure.config.WabaServiceProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Adapter for fetching WABA credentials from the external WABA service.
 * Deserializes the wire response (WabaCredentialsResponse) and maps it
 * to the internal DTO (WhatsappAccountCredentials) used across this service.
 */
@Slf4j
@Component
public class WabaCredentialAdapter implements WabaCredentialPort {

        private final WebClient webClient;
        private final WabaServiceProperties props;

        public WabaCredentialAdapter(
                        @Qualifier("wabaWebClient") WebClient webClient,
                        WabaServiceProperties props) {
                this.webClient = webClient;
                this.props = props;
        }

        @Override
        public AccessTokenIdentifier getWhatsappAccountWabaAccessToken(String wabaId, TenantScope tenantScope) {
                WhatsappAccountCredentials creds = fetchCredentials(wabaId, tenantScope);
                return new AccessTokenIdentifier(creds.getAccessToken());
        }

        @Override
        public WhatsappAccountCredentials getWhatsappAccountAppAccessToken(String wabaId, TenantScope tenantScope) {
                return fetchCredentials(wabaId, tenantScope);
        }

        /**
         * Single HTTP call shared by both public methods.
         * If the WABA service response shape changes, update WabaCredentialsResponse
         * and the mapping below.
         */
        private WhatsappAccountCredentials fetchCredentials(String wabaId, TenantScope tenant) {
                log.debug("Fetching WABA credentials for wabaId={}", wabaId);

                try {
                        WabaCredentialsResponse response = webClient.get()
                                        .uri(u -> u.path(props.path("get-credentials"))
                                                        .build(wabaId))
                                        .header(ApiHeaders.ORG_ID, String.valueOf(tenant.organizationId()))
                                        .header(ApiHeaders.PROJECT_ID, String.valueOf(tenant.projectId()))
                                        .retrieve()
                                        .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                                                        .map(body -> new WhatsappCredentialsNotFoundException(
                                                                        "WABA account not found for wabaId=" + wabaId
                                                                                        + " [" + r.statusCode() + "]: "
                                                                                        + body)))
                                        .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                                                        .map(body -> new WhatsappCredentialsNotFoundException(
                                                                        "WABA service error for wabaId=" + wabaId
                                                                                        + " [" + r.statusCode() + "]: "
                                                                                        + body)))
                                        .bodyToMono(WabaCredentialsResponse.class)
                                        .block();

                        if (response == null || response.getAccessToken() == null
                                        || response.getAccessToken().isBlank()) {
                                throw new WhatsappCredentialsNotFoundException(
                                                "Empty or null credentials returned for wabaId=" + wabaId);
                        }

                        // Map wire DTO → internal DTO
                        // wabaAccountId (Long) → wabaId (String), phoneNumberId → appId
                        WhatsappAccountCredentials creds = new WhatsappAccountCredentials();
                        creds.setWabaId(String.valueOf(response.getWabaAccountId()));
                        creds.setAppId(response.getPhoneNumberId());
                        creds.setAccessToken(response.getAccessToken());

                        log.debug("Resolved WABA credentials successfully for wabaId={}", wabaId);
                        return creds;

                } catch (WhatsappCredentialsNotFoundException e) {
                        throw e; // already descriptive, let it propagate
                } catch (WebClientResponseException e) {
                        log.error("HTTP error fetching WABA credentials wabaId={} status={}",
                                        wabaId, e.getStatusCode().value(), e);
                        throw new WhatsappCredentialsNotFoundException(
                                        "Failed to fetch WABA credentials for wabaId=" + wabaId
                                                        + " (HTTP " + e.getStatusCode().value() + ")");
                } catch (Exception e) {
                        log.error("Unexpected error fetching WABA credentials wabaId={}", wabaId, e);
                        throw new WhatsappCredentialsNotFoundException(
                                        "Unexpected error resolving WABA credentials for wabaId=" + wabaId
                                                        + ": " + e.getMessage());
                }
        }
}