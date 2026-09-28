package com.apargo.services.template.api.response.media;



import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class UploadSessionResponse {
    @JsonProperty("id")
    private String uploadSessionId;
}
