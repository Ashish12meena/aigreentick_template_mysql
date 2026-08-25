package com.aigreentick.services.template.infrastructure.client.account;

import com.aigreentick.services.template.application.dto.TenantScope;
import com.aigreentick.services.template.application.dto.client.AccessTokenIdentifier;
import com.aigreentick.services.template.application.dto.client.WhatsappAccountCredentials;
import com.aigreentick.services.template.application.port.out.WabaCredentialPort;
import com.aigreentick.services.template.common.constant.ApiHeaders;
import com.aigreentick.services.template.common.exception.WhatsappCredentialsNotFoundException;
import com.aigreentick.services.template.infrastructure.config.WebClientConfig;
import com.aigreentick.services.template.infrastructure.config.properties.WabaServiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Resolves WhatsApp Business Account credentials from waba-service.
 *
 * <h2>Authorization and retrieval are one operation</h2>
 *
 * waba-service answers {@code 404} when the WABA is outside the caller's
 * org/project — deliberately, so that "exists but is not yours" is
 * indistinguishable from "does not exist". This adapter therefore does not
 * check entitlement separately; there is no check-then-use window to race.
 *
 * <h2>Why every failure becomes one exception type</h2>
 *
 * A caller of this port can do exactly one thing when a credential cannot be
 * resolved: abandon the operation and report an upstream failure. Preserving
 * the distinction between "404 from waba-service", "connection refused" and
 * "malformed response" in the <em>type</em> would give call sites a choice
 * they have no use for. The distinction is preserved where it is actually
 * needed — in the log line — and
 * {@link WhatsappCredentialsNotFoundException} maps to {@code 502}, which
 * correctly attributes the failure to the upstream rather than to the
 * caller's request.
 *
 * <h2>Logging discipline</h2>
 *
 * Nothing here logs the token, any prefix or suffix of it, or its length. A
 * token fragment in a log is still a token fragment in whatever aggregator
 * ships those logs.
 */
@Slf4j
@Component
public class WabaCredentialAdapter implements WabaCredentialPort {

    private final WebClient webClient;
    private final WabaServiceProperties properties;
    private final WabaCredentialsMapper mapper;

    public WabaCredentialAdapter(
            @Qualifier(WebClientConfig.WABA_WEB_CLIENT) WebClient webClient,
            WabaServiceProperties properties,
            WabaCredentialsMapper mapper) {
        this.webClient = webClient;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public AccessTokenIdentifier getWhatsappAccountWabaAccessToken(String wabaId, TenantScope tenant) {
        return new AccessTokenIdentifier(fetchCredentials(wabaId, tenant).getAccessToken());
    }

    @Override
    public WhatsappAccountCredentials getWhatsappAccountAppAccessToken(String wabaId, TenantScope tenant) {
        return fetchCredentials(wabaId, tenant);
    }

    /**
     * The single HTTP call both public methods share.
     *
     * <p>The internal API key and the correlation header are attached
     * centrally by {@link WebClientConfig}, not here — every internal call
     * needs them, and an adapter that had to remember is an adapter that will
     * eventually forget. That is precisely what happened before: no key was
     * sent at all, which worked only because waba-service ships with
     * authentication disabled in its dev profile.
     */
    private WhatsappAccountCredentials fetchCredentials(String wabaId, TenantScope tenant) {
        log.debug("Resolving WABA credentials wabaId={} organizationId={} projectId={}",
                wabaId, tenant.organizationId(), tenant.projectId());

        try {
            WabaCredentialsResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.path(WabaServiceProperties.CREDENTIALS_BY_WABA))
                            .build(wabaId))
                    .header(ApiHeaders.ORG_ID, String.valueOf(tenant.organizationId()))
                    .header(ApiHeaders.PROJECT_ID, String.valueOf(tenant.projectId()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse
                            .bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> new WhatsappCredentialsNotFoundException(
                                    "waba-service returned " + clientResponse.statusCode().value()
                                            + " for wabaId=" + wabaId + ": " + body)))
                    .bodyToMono(WabaCredentialsResponse.class)
                    .block();

            if (response == null || response.getAccessToken() == null || response.getAccessToken().isBlank()) {
                throw new WhatsappCredentialsNotFoundException(
                        "waba-service returned no usable access token for wabaId=" + wabaId);
            }

            log.debug("Resolved WABA credentials wabaId={}", wabaId);
            return mapper.toWhatsappAccountCredentials(response);

        } catch (WhatsappCredentialsNotFoundException ex) {
            // Already carries the upstream status and body - do not re-wrap.
            throw ex;
        } catch (WebClientResponseException ex) {
            log.error("waba-service HTTP error wabaId={} status={}", wabaId, ex.getStatusCode().value(), ex);
            throw new WhatsappCredentialsNotFoundException(
                    "waba-service call failed for wabaId=" + wabaId
                            + " (HTTP " + ex.getStatusCode().value() + ")");
        } catch (Exception ex) {
            log.error("Unexpected error resolving WABA credentials wabaId={}", wabaId, ex);
            throw new WhatsappCredentialsNotFoundException(
                    "Unexpected error resolving WABA credentials for wabaId=" + wabaId);
        }
    }
}
