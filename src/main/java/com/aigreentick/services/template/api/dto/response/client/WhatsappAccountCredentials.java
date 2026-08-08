package com.aigreentick.services.template.api.dto.response.client;



import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class WhatsappAccountCredentials  { 
    private String wabaId;

    private String appId;

    private  String accessToken;
}

