package com.aigreentick.services.template.infrastructure.client.account;

import lombok.Getter;
import lombok.Setter;

/**
 * The wire shape of waba-service's
 * {@code GET /internal/v1/waba-credentials/by-waba/{wabaId}} response.
 *
 * <h2>Scope</h2>
 *
 * This is an infrastructure detail and stays package-private to the client
 * package by convention — {@link WabaCredentialsMapper} converts it to
 * {@code WhatsappAccountCredentials} at the boundary so no application class
 * ever depends on waba-service's field names.
 *
 * <p>Only the fields this service uses are declared. waba-service also
 * returns {@code organizationId}, {@code displayPhoneNumber},
 * {@code tokenType} and {@code expiresAt}; the decoder is configured to
 * ignore unknown properties, so its adding or removing an unrelated field
 * cannot break this client.
 *
 * <h2>Field naming</h2>
 *
 * waba-service serializes camelCase. This service's own API is snake_case,
 * so the decoder for this client is pinned explicitly in
 * {@code WebClientConfig} rather than inheriting the global setting.
 */
@Getter
@Setter
public class WabaCredentialsResponse {

    /** waba-service's internal row id. Not a Meta identifier - do not send it to Meta. */
    private Long wabaAccountId;

    /** Meta's globally unique WABA id. This is the one Meta accepts. */
    private String wabaId;

    /** Meta's Phone Number ID. Populated only when resolved by phone number. */
    private String phoneNumberId;

    /** Decrypted Meta access token. Treat as a secret; never log it. */
    private String accessToken;
}
