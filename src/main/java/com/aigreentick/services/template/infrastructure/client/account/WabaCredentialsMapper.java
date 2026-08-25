package com.aigreentick.services.template.infrastructure.client.account;

import com.aigreentick.services.template.application.dto.client.WhatsappAccountCredentials;
import org.springframework.stereotype.Component;

/**
 * Maps waba-service's wire response onto the internal credential DTO.
 *
 * <h2>Why this class had to start being used</h2>
 *
 * It already existed, and {@code WabaCredentialAdapter} ignored it and did
 * the mapping inline instead. The two copies had already diverged on the
 * field that matters most: this mapper read {@code response.getWabaId()} —
 * Meta's real WABA id — while the adapter used
 * {@code String.valueOf(response.getWabaAccountId())}, waba-service's
 * internal primary key. The adapter's version was the one actually running.
 *
 * <p>There is now one mapping, and the adapter calls it.
 */
@Component
public class WabaCredentialsMapper {

    public WhatsappAccountCredentials toWhatsappAccountCredentials(WabaCredentialsResponse response) {
        return WhatsappAccountCredentials.builder()
                .wabaId(response.getWabaId())
                .phoneNumberId(response.getPhoneNumberId())
                .accessToken(response.getAccessToken())
                .build();
    }
}
