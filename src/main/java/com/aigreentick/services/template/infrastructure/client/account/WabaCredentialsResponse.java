package com.aigreentick.services.template.infrastructure.client.account;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WabaCredentialsResponse {

    // @Schema(description = "Internal WABA account DB ID", example = "7001")
    private Long wabaAccountId;

    private String wabaId;

    // @Schema(description = "Meta phone number ID", example = "123456789012345")
    private String phoneNumberId;

    // @Schema(description = "Decrypted Meta access token", example = "EAABwzLixnjYBO...")
    private String accessToken;
}