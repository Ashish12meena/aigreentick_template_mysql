package com.aigreentick.services.template.infrastructure.client.account;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.dto.response.client.WhatsappAccountCredentials;

@Component
public class WabaCredentialsMapper {

    public WhatsappAccountCredentials toWhatsappAccountCredentials(WabaCredentialsResponse response) {
        WhatsappAccountCredentials creds = new WhatsappAccountCredentials();
        creds.setWabaId(response.getWabaId()); 
        creds.setAppId(response.getPhoneNumberId());
        creds.setAccessToken(response.getAccessToken());
        return creds;
    }
}