package com.aigreentick.services.template.application.port.out;

import com.aigreentick.services.template.application.dto.client.AccessTokenIdentifier;
import com.aigreentick.services.template.application.dto.client.WhatsappAccountCredentials;
import com.aigreentick.services.template.application.dto.TenantScope;

/**
 * Port for resolving WhatsApp Business Account credentials.
 * Application layer depends on this interface — never on the HTTP adapter directly.
 */
public interface WabaCredentialPort {

    /**
     * Resolves the access token for {@code wabaId}, scoped to the caller's tenant.
     * waba-service returns 404 if the WABA does not belong to this org/project —
     * authorization and retrieval are one atomic operation, so there is no
     * check-then-use window.
     */
    AccessTokenIdentifier getWhatsappAccountWabaAccessToken(String wabaId, TenantScope tenant);

    WhatsappAccountCredentials getWhatsappAccountAppAccessToken(String wabaId, TenantScope tenant);
}