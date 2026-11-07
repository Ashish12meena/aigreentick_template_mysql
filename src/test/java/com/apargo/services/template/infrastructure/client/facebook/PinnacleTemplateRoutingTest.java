package com.apargo.services.template.infrastructure.client.facebook;

import com.apargo.services.template.application.dto.client.AccessTokenIdentifier;
import com.apargo.services.template.application.dto.client.FacebookApiResponse;
import com.apargo.services.template.common.exception.WhatsappCredentialsNotFoundException;
import com.apargo.services.template.infrastructure.client.account.TemplateRoutePolicy;
import com.apargo.services.template.infrastructure.client.account.WabaCredentialsResponse;
import com.apargo.services.template.infrastructure.client.facebook.endpoint.MetaTemplateEndpoint;
import com.apargo.services.template.infrastructure.client.facebook.endpoint.PinnacleTemplateEndpoint;
import com.apargo.services.template.infrastructure.client.facebook.endpoint.TemplateEndpointRegistry;
import com.apargo.services.template.infrastructure.config.properties.FacebookClientProperties;
import com.apargo.services.template.infrastructure.config.properties.TemplateProviderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Template create and delete routed by the WABA's provider (doc 47 R4), over real HTTP: Pinnacle's
 * {@code /v3/{wabaId}/message_templates} with {@code apikey}, Meta's Graph path with a bearer token.
 */
class PinnacleTemplateRoutingTest {

    private static final String WABA = "1433508011972817";
    private static final String BODY = "{\"name\":\"t\",\"language\":\"en\",\"category\":\"UTILITY\",\"components\":[]}";
    private static final AccessTokenIdentifier PINNACLE = AccessTokenIdentifier.bsp("PINNACLE", "meta-token", "pin-key");
    private static final AccessTokenIdentifier META = new AccessTokenIdentifier("meta-token");

    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> pathAndQuery = new AtomicReference<>();
    private final AtomicReference<Headers> headers = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private volatile String response = "{\"id\":\"111\",\"status\":\"PENDING\",\"category\":\"UTILITY\"}";

    private HttpServer server;
    private FacebookTemplateAdapter adapter;
    private TemplateEndpointRegistry registry;
    private TemplateProviderProperties providers;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            method.set(exchange.getRequestMethod());
            String query = exchange.getRequestURI().getRawQuery();
            pathAndQuery.set(exchange.getRequestURI().getPath() + (query == null ? "" : "?" + query));
            headers.set(exchange.getRequestHeaders());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        FacebookClientProperties facebook = new FacebookClientProperties();
        facebook.setBaseUrl(base);
        facebook.setApiVersion("v23.0");

        providers = new TemplateProviderProperties();
        TemplateProviderProperties.Provider pinnacle = new TemplateProviderProperties.Provider();
        pinnacle.setEnabled(true);
        pinnacle.setBaseUrl(base);
        pinnacle.setApiVersion("v3");
        providers.setProviders(Map.of("pinnacle", pinnacle));

        registry = new TemplateEndpointRegistry(List.of(
                new MetaTemplateEndpoint(facebook), new PinnacleTemplateEndpoint(providers)));
        adapter = new FacebookTemplateAdapter(WebClient.create(), facebook, registry);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    void aPinnacleCreateGoesToPinnaclesPathWithTheApiKeyAndTheSameBody() {
        FacebookApiResponse<JsonNode> result = adapter.createTemplate(BODY, WABA, PINNACLE);

        assertThat(result.isSuccess()).isTrue();
        assertThat(method.get()).isEqualTo("POST");
        assertThat(pathAndQuery.get()).isEqualTo("/v3/" + WABA + "/message_templates");
        assertThat(headers.get().getFirst("apikey")).isEqualTo("pin-key");
        assertThat(headers.get().containsKey("Authorization")).isFalse();
        assertThat(body.get()).isEqualTo(BODY);
    }

    @Test
    void pinnaclesWrappedAnswerIsReadInMetasShape() {
        response = "{\"response\":{\"id\":\"222\",\"status\":\"PENDING\",\"category\":\"MARKETING\"}}";

        FacebookApiResponse<JsonNode> result = adapter.createTemplate(BODY, WABA, PINNACLE);

        assertThat(result.getData().get("id").asText()).isEqualTo("222");
        assertThat(result.getData().get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void aMetaCreateIsUnchanged() {
        FacebookApiResponse<JsonNode> result = adapter.createTemplate(BODY, WABA, META);

        assertThat(result.isSuccess()).isTrue();
        assertThat(pathAndQuery.get()).isEqualTo("/v23.0/" + WABA + "/message_templates");
        assertThat(headers.get().getFirst("Authorization")).isEqualTo("Bearer meta-token");
        assertThat(headers.get().containsKey("apikey")).isFalse();
    }

    @Test
    void aPinnacleRejectionKeepsItsStatusAndBody() {
        status = 400;
        response = "{\"error\":{\"message\":\"Invalid parameter\",\"code\":100}}";

        FacebookApiResponse<JsonNode> result = adapter.createTemplate(BODY, WABA, PINNACLE);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(400);
        assertThat(result.getErrorMessage()).contains("Invalid parameter");
    }

    @Test
    void aPinnacleRouteWithoutAKeyFailsBeforeAnyRequestAndNeverGoesToMeta() {
        AccessTokenIdentifier noKey = AccessTokenIdentifier.bspUnavailable("PINNACLE", "meta-token",
                "Pinnacle credential 210 of WABA " + WABA + " is REVOKED");

        assertThatThrownBy(() -> adapter.createTemplate(BODY, WABA, noKey))
                .isInstanceOf(WhatsappCredentialsNotFoundException.class)
                .hasMessageContaining("REVOKED");
        assertThat(calls.get()).isZero();
    }

    @Test
    void aProviderWithoutAnEndpointFailsBeforeAnyRequest() {
        assertThatThrownBy(() -> adapter.createTemplate(BODY, WABA,
                AccessTokenIdentifier.bsp("NEWBSP", "t", "k")))
                .isInstanceOf(WhatsappCredentialsNotFoundException.class)
                .hasMessageContaining("No template endpoint is registered for provider NEWBSP");
        assertThat(calls.get()).isZero();
    }

    // ── delete ──────────────────────────────────────────────────────────────

    @Test
    void aPinnacleDeleteGoesToPinnacleWithNameAndHsmId() {
        response = "{\"success\":true}";

        FacebookApiResponse<JsonNode> result = adapter.deleteTemplate("pinnacle_route_test", "1385268870348328",
                WABA, PINNACLE);

        assertThat(result.isSuccess()).isTrue();
        assertThat(method.get()).isEqualTo("DELETE");
        assertThat(pathAndQuery.get()).isEqualTo("/v3/" + WABA
                + "/message_templates?name=pinnacle_route_test&hsm_id=1385268870348328");
        assertThat(headers.get().getFirst("apikey")).isEqualTo("pin-key");
    }

    @Test
    void aMetaDeleteIsUnchanged() {
        response = "{\"success\":true}";

        adapter.deleteTemplate("t", "99", WABA, META);

        assertThat(pathAndQuery.get()).isEqualTo("/v23.0/" + WABA + "/message_templates?name=t&hsm_id=99");
        assertThat(headers.get().getFirst("Authorization")).isEqualTo("Bearer meta-token");
    }

    // ── route policy ────────────────────────────────────────────────────────

    private static WabaCredentialsResponse answer(String provider, String apiKey, String reason) {
        WabaCredentialsResponse response = new WabaCredentialsResponse();
        response.setWabaId(WABA);
        response.setAccessToken("meta-token");
        response.setProvider(provider);
        if (apiKey != null) {
            WabaCredentialsResponse.BspPart bsp = new WabaCredentialsResponse.BspPart();
            bsp.setApiKey(apiKey);
            response.setBsp(bsp);
        }
        response.setBspUnavailableReason(reason);
        return response;
    }

    @Test
    void theRoutePolicyFollowsTheSwitchAndTheAllowlist() {
        TemplateRoutePolicy policy = new TemplateRoutePolicy(providers, registry);

        assertThat(policy.route(WABA, answer(null, null, null)).isMeta()).isTrue();
        assertThat(policy.route(WABA, answer("META_DIRECT", null, null)).isMeta()).isTrue();

        AccessTokenIdentifier routed = policy.route(WABA, answer("PINNACLE", "pin-key", null));
        assertThat(routed.getProvider()).isEqualTo("PINNACLE");
        assertThat(routed.getBspApiKey()).isEqualTo("pin-key");
        assertThat(routed.getAccessToken()).isEqualTo("meta-token");

        AccessTokenIdentifier noKey = policy.route(WABA, answer("PINNACLE", null, "credential REVOKED"));
        assertThat(noKey.getProvider()).isEqualTo("PINNACLE");
        assertThat(noKey.usable()).isFalse();
        assertThat(noKey.getUnavailableReason()).isEqualTo("credential REVOKED");

        providers.find("pinnacle").orElseThrow().setWabaAllowlist(List.of("other-waba"));
        assertThat(policy.route(WABA, answer("PINNACLE", "pin-key", null)).isMeta()).isTrue();

        providers.find("pinnacle").orElseThrow().setWabaAllowlist(List.of(WABA));
        assertThat(policy.route(WABA, answer("PINNACLE", "pin-key", null)).getProvider()).isEqualTo("PINNACLE");

        providers.find("pinnacle").orElseThrow().setEnabled(false);
        assertThat(policy.route(WABA, answer("PINNACLE", "pin-key", null)).isMeta()).isTrue();
    }

    @Test
    void neitherSecretIsEverPrinted() {
        assertThat(PINNACLE.toString()).doesNotContain("meta-token").doesNotContain("pin-key").contains("PINNACLE");
        assertThat(answer("PINNACLE", "pin-key", null).toString()).doesNotContain("meta-token").doesNotContain("pin-key");
    }

    @Test
    void twoEndpointsForOneProviderFailAtStartup() {
        assertThatThrownBy(() -> new TemplateEndpointRegistry(List.of(
                new PinnacleTemplateEndpoint(providers), new PinnacleTemplateEndpoint(providers))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Two template endpoints for provider PINNACLE");
    }
}
