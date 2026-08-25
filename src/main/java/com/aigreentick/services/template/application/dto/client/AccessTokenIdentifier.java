package com.aigreentick.services.template.application.dto.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Just the access token, for the call paths that need nothing else.
 *
 * <p>Kept as a wrapper rather than a bare {@code String} so a token cannot be
 * passed to a method expecting some other string, and so it never accidentally
 * lands in a log line through ordinary string concatenation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessTokenIdentifier {

    /** Decrypted Meta access token. Treat as a secret; never log it. */
    private String accessToken;

    /**
     * Deliberately does not include the token. Lombok's generated
     * {@code toString} would otherwise print it in full the first time this
     * object appears in a log statement or an exception message.
     */
    @Override
    public String toString() {
        return "AccessTokenIdentifier(accessToken=***)";
    }
}
