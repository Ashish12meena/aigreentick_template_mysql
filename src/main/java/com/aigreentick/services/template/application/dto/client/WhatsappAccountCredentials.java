package com.aigreentick.services.template.application.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A resolved WhatsApp Business Account credential, as returned by
 * waba-service.
 *
 * <h2>Naming</h2>
 *
 * The field carrying Meta's Phone Number ID used to be called {@code appId},
 * which named neither what waba-service sends ({@code phoneNumberId}) nor
 * what Meta calls it. A Meta "App ID" is a genuinely different identifier, so
 * the old name did not just read badly — it pointed at the wrong concept, and
 * anyone reaching for the real app id would have found this field and used it.
 *
 * <p>{@link #wabaId} is Meta's globally unique WABA id (a numeric string),
 * <em>not</em> waba-service's internal row id. The adapter previously
 * populated it with {@code String.valueOf(wabaAccountId)} — waba-service's
 * internal primary key — while ignoring the real {@code wabaId} the same
 * response carried. Anything that fed this value back to Meta would have been
 * addressing an account that does not exist.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WhatsappAccountCredentials {

    /** Meta's globally unique WhatsApp Business Account id. */
    private String wabaId;

    /** Meta's Phone Number ID. Null when the credential was resolved by WABA. */
    private String phoneNumberId;

    /** Decrypted Meta access token. Treat as a secret; never log it. */
    private String accessToken;
}
