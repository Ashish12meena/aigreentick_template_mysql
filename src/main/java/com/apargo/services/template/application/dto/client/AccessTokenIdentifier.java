package com.apargo.services.template.application.dto.client;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The credential a template call is made with, already routed (doc 47 R4).
 *
 * <p>{@link #accessToken} is the WABA's Meta token and is always present. {@link #provider} is the
 * route for template <em>create and delete</em>: {@value #META_DIRECT} (Meta Graph with the token),
 * or a BSP code such as {@code PINNACLE} (that BSP's API with {@link #bspApiKey}). Reads, edits and
 * header samples always use Meta. The application layer never looks at the provider; it passes this
 * object to the port.
 *
 * <p>Kept as a wrapper rather than a bare {@code String} so a token cannot be passed to a method
 * expecting some other string, and so it never lands in a log line through string concatenation.
 */
@Data
@NoArgsConstructor
public class AccessTokenIdentifier {

    public static final String META_DIRECT = "META_DIRECT";

    /** Decrypted Meta access token. Treat as a secret; never log it. */
    private String accessToken;

    /** Route for create and delete: {@value #META_DIRECT} or a BSP code. */
    private String provider = META_DIRECT;

    /** The BSP key when {@link #provider} is a BSP and the key is usable. A secret. */
    private String bspApiKey;

    /** Why a BSP route has no key; null otherwise. */
    private String unavailableReason;

    /** Meta only — every WABA before BSP routing, and every META_DIRECT WABA. */
    public AccessTokenIdentifier(String accessToken) {
        this.accessToken = accessToken;
    }

    public static AccessTokenIdentifier bsp(String provider, String accessToken, String bspApiKey) {
        AccessTokenIdentifier credential = new AccessTokenIdentifier(accessToken);
        credential.provider = provider;
        credential.bspApiKey = bspApiKey;
        return credential;
    }

    public static AccessTokenIdentifier bspUnavailable(String provider, String accessToken, String reason) {
        AccessTokenIdentifier credential = new AccessTokenIdentifier(accessToken);
        credential.provider = provider;
        credential.unavailableReason = reason == null ? "No usable " + provider + " credential" : reason;
        return credential;
    }

    public boolean isMeta() {
        return provider == null || META_DIRECT.equalsIgnoreCase(provider);
    }

    /** True when the create/delete route can be used: Meta with a token, or a BSP with a key. */
    public boolean usable() {
        String secret = isMeta() ? accessToken : bspApiKey;
        return secret != null && !secret.isBlank();
    }

    /**
     * Deliberately does not include either secret. Lombok's generated {@code toString} would
     * otherwise print them in full the first time this object appears in a log statement or an
     * exception message.
     */
    @Override
    public String toString() {
        return "AccessTokenIdentifier(provider=" + provider
                + ", accessToken=" + (accessToken == null ? null : "***")
                + ", bspApiKey=" + (bspApiKey == null ? null : "***")
                + ", unavailableReason=" + unavailableReason + ")";
    }
}
