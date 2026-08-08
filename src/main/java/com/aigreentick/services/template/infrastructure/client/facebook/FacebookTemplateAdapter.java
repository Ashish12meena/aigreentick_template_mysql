package com.aigreentick.services.template.infrastructure.client.facebook;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.aigreentick.services.template.api.dto.response.client.FacebookApiResponse;
import com.aigreentick.services.template.api.dto.response.media.UploadMediaResponse;
import com.aigreentick.services.template.api.dto.response.media.UploadOffsetResponse;
import com.aigreentick.services.template.api.dto.response.media.UploadSessionResponse;
import com.aigreentick.services.template.application.port.out.FacebookMediaUploadPort;
import com.aigreentick.services.template.application.port.out.FacebookTemplatePort;
import com.aigreentick.services.template.application.port.out.FacebookTemplateSyncPort;
import com.aigreentick.services.template.infrastructure.config.FacebookClientProperties;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class FacebookTemplateAdapter  implements FacebookTemplatePort, FacebookTemplateSyncPort, FacebookMediaUploadPort{

        private final WebClient webClient;
        private final FacebookClientProperties properties;

        /**
         * Build WebClient once at construction time — reuse for all calls.
         * WebClient is immutable and thread-safe after build().
         */
        public FacebookTemplateAdapter(WebClient.Builder webClientBuilder, FacebookClientProperties properties) {
                this.properties = properties;
                this.webClient = webClientBuilder.build();
        }

        /**
         * Sends a WhatsApp template to Facebook for approval.
         */
        public FacebookApiResponse<JsonNode> createTemplate(String bodyJson, String wabaId, String accessToken) {

                URI uri = UriComponentsBuilder
                                .fromUriString(properties.getBaseUrl())
                                .pathSegment(properties.getApiVersion(), wabaId, "message_templates")
                                .build()
                                .toUri();

                try {
                        JsonNode response = webClient
                                        .post()
                                        .uri(uri)
                                        .headers(headers -> headers.setBearerAuth(accessToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(bodyJson)
                                        .retrieve()
                                        .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook API 4xx error for WABA_ID={}: {}",
                                                                                wabaId, errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 4xx: "
                                                                                                + errorBody));
                                                        }))
                                        .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook API 5xx error for WABA_ID={}: {}",
                                                                                wabaId, errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 5xx: "
                                                                                                + errorBody));
                                                        }))
                                        .bodyToMono(JsonNode.class)
                                        .block();

                        log.info("Template sent to Facebook. WABA_ID={} Response={}", wabaId, response);
                        return FacebookApiResponse.success(response, 200);

                } catch (WebClientResponseException ex) {
                        log.error("Failed to send template. WABA_ID={} Status={} Response={}", wabaId,
                                        ex.getStatusCode().value(), ex.getResponseBodyAsString());
                        return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

                } catch (Exception ex) {
                        log.error("Unexpected error while sending template to Facebook. WABA_ID={}", wabaId, ex);
                        return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
                }
        }

        /**
         * Fetches all WhatsApp templates for a given WABA.
         */
        public FacebookApiResponse<JsonNode> getAllTemplates(
                        String wabaId,
                        String accessToken,
                        Optional<String> status,
                        Optional<String> language,
                        Optional<String> category,
                        Optional<String> name,
                        Optional<Integer> limit,
                Optional<String> after) {

                UriComponentsBuilder builder = UriComponentsBuilder
                                .fromUriString(properties.getBaseUrl())
                                .pathSegment(properties.getApiVersion(), wabaId, "message_templates");

                status.ifPresent(s -> builder.queryParam("status", s));
                language.ifPresent(l -> builder.queryParam("language", l));
                category.ifPresent(c -> builder.queryParam("category", c));
                name.ifPresent(n -> builder.queryParam("name", n));
                limit.ifPresent(l -> builder.queryParam("limit", l));
                after.ifPresent(a -> builder.queryParam("after", a));

                URI uri = builder.build().toUri();

                try {
                        JsonNode response = webClient
                                        .get()
                                        .uri(uri)
                                        .headers(headers -> headers.setBearerAuth(accessToken))
                                        .retrieve()
                                        .onStatus(HttpStatusCode::is4xxClientError,
                                                        r -> Mono.error(new RuntimeException(
                                                                        "Facebook API returned 4xx")))
                                        .onStatus(HttpStatusCode::is5xxServerError,
                                                        r -> Mono.error(new RuntimeException(
                                                                        "Facebook API returned 5xx")))
                                        .bodyToMono(JsonNode.class)
                                        .block();

                        return FacebookApiResponse.success(response, 200);

                } catch (WebClientResponseException ex) {
                        log.error("Failed to fetch all templates. WABA_ID={} URI={} Status={} Response={}", wabaId, uri,
                                        ex.getStatusCode(), ex.getResponseBodyAsString());
                        return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

                } catch (Exception ex) {
                        log.error("Unexpected error while fetching templates. WABA_ID={} URI={}", wabaId, uri, ex);
                        return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
                }
        }

        /**
         * Deletes a WhatsApp template from Facebook by name.
         */
        public FacebookApiResponse<JsonNode> deleteTemplate(
                        String templateName, String wabaId, String accessToken) {

                URI uri = UriComponentsBuilder
                                .fromUriString(properties.getBaseUrl())
                                .pathSegment(properties.getApiVersion(), wabaId, "message_templates")
                                .queryParam("name", templateName)
                                .build()
                                .toUri();

                try {
                        JsonNode response = webClient
                                        .delete()
                                        .uri(uri)
                                        .headers(headers -> headers.setBearerAuth(accessToken))
                                        .retrieve()
                                        .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook DELETE 4xx error for WABA_ID={}: {}",
                                                                                wabaId, errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 4xx: "
                                                                                                + errorBody));
                                                        }))
                                        .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook DELETE 5xx error for WABA_ID={}: {}",
                                                                                wabaId, errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 5xx: "
                                                                                                + errorBody));
                                                        }))
                                        .bodyToMono(JsonNode.class)
                                        .block();

                        log.info("Template deleted from Facebook. WABA_ID={} name={} Response={}", wabaId, templateName,
                                        response);
                        return FacebookApiResponse.success(response, 200);

                } catch (WebClientResponseException ex) {
                        log.error("Failed to delete template from Facebook. WABA_ID={} name={} Status={}",
                                        wabaId, templateName, ex.getStatusCode().value());
                        return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

                } catch (Exception ex) {
                        log.error("Unexpected error deleting template from Facebook. WABA_ID={} name={}", wabaId,
                                        templateName, ex);
                        return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
                }
        }

        /**
         * Step 1: Initiates an upload session with the Facebook Graph API.
         */
        public FacebookApiResponse<UploadSessionResponse> initiateUploadSession(String fileName, long fileSize,
                        String mimeType,
                        String wabaAppId, String accessToken) {

                URI uri = UriComponentsBuilder
                                .fromUriString(properties.getBaseUrl())
                                .pathSegment(properties.getApiVersion(), wabaAppId, "uploads")
                                .queryParam("file_name", fileName)
                                .queryParam("file_length", fileSize)
                                .queryParam("file_type", mimeType)
                                .queryParam("access_token", accessToken)
                                .build()
                                .toUri();

                log.info("Initiating upload session: {}", uri);

                try {
                        UploadSessionResponse response = webClient
                                        .post()
                                        .uri(uri)
                                        .accept(MediaType.APPLICATION_JSON)
                                        .retrieve()
                                        .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook API 4xx during upload initiation for appId={}: {}",
                                                                                wabaAppId,
                                                                                errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 4xx: "
                                                                                                + errorBody));
                                                        }))
                                        .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook API 5xx during upload initiation for appId={}: {}",
                                                                                wabaAppId,
                                                                                errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 5xx: "
                                                                                                + errorBody));
                                                        }))
                                        .bodyToMono(UploadSessionResponse.class)
                                        .block();

                        log.info("Upload session initiated successfully. Session ID: {}",
                                        response.getUploadSessionId());
                        return FacebookApiResponse.success(response, 200);

                } catch (WebClientResponseException ex) {
                        log.error("Upload session initiation failed. AppId={} Status={} Response={}",
                                        wabaAppId, ex.getStatusCode().value(), ex.getResponseBodyAsString());
                        return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

                } catch (Exception ex) {
                        log.error("Unexpected error initiating upload session for AppId={}", wabaAppId, ex);
                        return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
                }
        }

        /**
         * Step 2: Uploads media to Facebook using an upload session ID.
         */
        public FacebookApiResponse<UploadMediaResponse> uploadResumableMediaToFacebook(
                        String sessionId,
                        File file,
                        String accessToken,
                        String offset) throws IOException {

                if (!file.exists()) {
                        return FacebookApiResponse.error("File not found: " + file.getAbsolutePath(), 400);
                }

                URI uri = URI.create(properties.getBaseUrl() + "/" + properties.getApiVersion() + "/" + sessionId);
                log.info("Uploading media chunk to Facebook: {}", uri);

                try {
                        FileSystemResource fileResource = new FileSystemResource(file);

                        UploadMediaResponse response = webClient
                                        .post()
                                        .uri(uri)
                                        .header(HttpHeaders.AUTHORIZATION, "OAuth " + accessToken.trim())
                                        .header("file_offset", String.valueOf(offset).trim())
                                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                        .body(BodyInserters.fromResource(fileResource))
                                        .retrieve()
                                        .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook API 4xx during media upload for sessionId={}: {}",
                                                                                sessionId,
                                                                                errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 4xx: "
                                                                                                + errorBody));
                                                        }))
                                        .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Facebook API 5xx during media upload for sessionId={}: {}",
                                                                                sessionId,
                                                                                errorBody);
                                                                return Mono.error(new RuntimeException(
                                                                                "Facebook API returned 5xx: "
                                                                                                + errorBody));
                                                        }))
                                        .bodyToMono(UploadMediaResponse.class)
                                        .block();

                        if (response == null || response.getFacebookImageUrl() == null) {
                                throw new IllegalStateException("Upload failed or handle not returned");
                        }

                        log.info("Media uploaded successfully. Handle={}", response.getFacebookImageUrl());
                        return FacebookApiResponse.success(response, 200);

                } catch (WebClientResponseException ex) {
                        log.error("Failed to upload media. SessionId={} Status={} Response={}",
                                        sessionId, ex.getStatusCode().value(), ex.getResponseBodyAsString());
                        return FacebookApiResponse.error(ex.getResponseBodyAsString(), ex.getStatusCode().value());

                } catch (Exception ex) {
                        log.error("Unexpected error during media upload. SessionId={}", sessionId, ex);
                        return FacebookApiResponse.error("Internal Server Error: " + ex.getMessage(), 500);
                }
        }

        /**
         * Step 3: Retrieves current file offset for an ongoing upload session.
         */
        public UploadOffsetResponse getUploadOffset(String sessionId, String accessToken) {

                URI uri = UriComponentsBuilder
                                .fromUriString(properties.getBaseUrl())
                                .pathSegment(properties.getApiVersion(), sessionId)
                                .queryParam("access_token", accessToken)
                                .build()
                                .toUri();

                log.info("Checking upload offset: {}", uri);

                return webClient
                                .get()
                                .uri(uri)
                                .header(HttpHeaders.AUTHORIZATION, "OAuth " + accessToken)
                                .accept(MediaType.APPLICATION_JSON)
                                .retrieve()
                                .bodyToMono(UploadOffsetResponse.class)
                                .doOnNext(resp -> log.info("Received file_offset: {}", resp.getFileOffset()))
                                .block();
        }

        @SuppressWarnings("unused")
        private FacebookApiResponse<JsonNode> getTemplateByNameFallback(
                        String templateName, String wabaId, String accessToken, Throwable ex) {
                log.error("Fallback triggered while fetching template by name. Name={} WABA_ID={}", templateName,
                                wabaId, ex);
                return FacebookApiResponse.error("Fallback triggered: " + ex.getMessage(), 500);
        }

        @SuppressWarnings("unused")
        private FacebookApiResponse<JsonNode> createTemplateFallback(String bodyJson, String wabaId,
                        String accessToken, Throwable ex) {
                log.error("Fallback triggered while sending template to Facebook. WABA_ID={}", wabaId, ex);
                return FacebookApiResponse.error("Fallback triggered: " + ex.getMessage(), 500);
        }

        @SuppressWarnings("unused")
        private FacebookApiResponse<JsonNode> getAllTemplatesFallback(
                        String wabaId,
                        String accessToken,
                        Optional<String> status,
                        Optional<String> language,
                        Optional<String> category,
                        Optional<String> name,
                        Optional<Integer> limit,
                        Throwable ex) {
                log.error("Fallback triggered while fetching all templates. WABA_ID={}", wabaId, ex);
                return FacebookApiResponse.error("Fallback triggered: " + ex.getMessage(), 500);
        }
}