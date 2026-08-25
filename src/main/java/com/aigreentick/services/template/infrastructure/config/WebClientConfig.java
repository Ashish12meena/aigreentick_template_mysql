package com.aigreentick.services.template.infrastructure.config;

import com.aigreentick.services.template.common.constant.ApiHeaders;
import com.aigreentick.services.template.common.constant.InternalHeaders;
import com.aigreentick.services.template.common.constant.LogKeys;
import com.aigreentick.services.template.infrastructure.config.properties.FacebookClientProperties;
import com.aigreentick.services.template.infrastructure.config.properties.InternalApiProperties;
import com.aigreentick.services.template.infrastructure.config.properties.MediaServiceProperties;
import com.aigreentick.services.template.infrastructure.config.properties.WabaServiceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * Outbound HTTP clients, one per upstream.
 *
 * <h2>The naming-strategy trap this class exists to close</h2>
 *
 * This service sets {@code spring.jackson.property-naming-strategy:
 * SNAKE_CASE} globally, because its own public API is snake_case and that is
 * a frozen contract. But <em>none</em> of its upstreams are: waba-service and
 * storage-service both serialize camelCase.
 *
 * <p>Previously that mismatch was avoided entirely by accident. The
 * {@code WebClient.Builder} bean here was declared as a bare
 * {@code WebClient.builder()}, which does <strong>not</strong> pick up
 * Spring Boot's context {@link ObjectMapper} and therefore fell back to
 * Jackson defaults — camelCase — which happened to be correct. Nothing said
 * so. Anyone injecting Spring Boot's auto-configured
 * {@code WebClient.Builder} instead, or adding
 * {@code spring.jackson} customisation, would have silently switched these
 * clients to snake_case decoding. The symptom would not have been an
 * exception: {@code fail-on-unknown-properties} is off, so every field would
 * simply deserialize to {@code null}. A credential lookup would return a
 * token of {@code null} and surface as "credentials not found"; a batch
 * media upload would return URLs of {@code null} and surface as templates
 * silently missing their images.
 *
 * <p>Each client below therefore configures its codecs with an
 * <em>explicit</em> camelCase {@link ObjectMapper}. The behaviour is
 * unchanged; it is now stated rather than inherited by luck.
 *
 * <h2>Timeouts and buffer limits are per-upstream</h2>
 *
 * A credential lookup and a twenty-file media upload have nothing in common,
 * so they do not share a timeout. Likewise the Meta client raises the codec
 * buffer limit, because a WABA with several hundred templates returns a sync
 * payload well past WebClient's 256 KB default.
 */
@Slf4j
@Configuration
public class WebClientConfig {

    /** Bean name for the waba-service client. */
    public static final String WABA_WEB_CLIENT = "wabaWebClient";

    /** Bean name for the storage-service client. */
    public static final String MEDIA_WEB_CLIENT = "mediaWebClient";

    /** Bean name for the Meta Graph API client. */
    public static final String FACEBOOK_WEB_CLIENT = "facebookWebClient";

    // ----------------------------------------------------
    // Clients
    // ----------------------------------------------------

    @Bean(WABA_WEB_CLIENT)
    public WebClient wabaWebClient(WabaServiceProperties properties, InternalApiProperties internalApi) {
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(connector(properties.getConnectTimeout(), properties.getReadTimeout()))
                .codecs(c -> applyCamelCaseCodecs(c, 1024 * 1024))
                .filter(internalAuth(internalApi))
                .filter(correlation())
                .filter(logFailures(WABA_WEB_CLIENT))
                .build();
    }

    @Bean(MEDIA_WEB_CLIENT)
    public WebClient mediaWebClient(MediaServiceProperties properties, InternalApiProperties internalApi) {
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(connector(properties.getConnectTimeout(), properties.getReadTimeout()))
                .codecs(c -> applyCamelCaseCodecs(c, properties.getMaxInMemorySizeBytes()))
                .filter(internalAuth(internalApi))
                .filter(correlation())
                .filter(logFailures(MEDIA_WEB_CLIENT))
                .build();
    }

    /**
     * Meta is an external third party, so it gets neither the internal API
     * key nor the internal correlation header — leaking either outside the
     * trust boundary is exactly what those headers must not do.
     */
    @Bean(FACEBOOK_WEB_CLIENT)
    public WebClient facebookWebClient(FacebookClientProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(connector(properties.getConnectTimeout(), properties.getReadTimeout()))
                .codecs(c -> applyCamelCaseCodecs(c, properties.getMaxInMemorySizeBytes()))
                .filter(logFailures(FACEBOOK_WEB_CLIENT))
                .build();
    }

    // ----------------------------------------------------
    // Shared building blocks
    // ----------------------------------------------------

    private ReactorClientHttpConnector connector(int connectTimeoutMs, int readTimeoutMs) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS)));
        return new ReactorClientHttpConnector(httpClient);
    }

    /**
     * Explicit camelCase codecs. See the class Javadoc for why relying on the
     * default here would be a trap rather than a convenience.
     */
    private void applyCamelCaseCodecs(org.springframework.http.codec.ClientCodecConfigurer configurer,
                                      int maxInMemorySize) {
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .findAndRegisterModules()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        configurer.defaultCodecs().maxInMemorySize(maxInMemorySize);
        configurer.defaultCodecs().jackson2JsonEncoder(
                new Jackson2JsonEncoder(mapper, MediaType.APPLICATION_JSON));
        configurer.defaultCodecs().jackson2JsonDecoder(
                new Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON));
    }

    /**
     * Presents the shared internal key on every call to a sibling service.
     *
     * <p>Before this existed the WABA credential adapter sent no key at all.
     * That worked only because waba-service ships
     * {@code internal.api.auth-enabled: false} in its dev profile — the first
     * environment to turn authentication on would have failed every template
     * create, submit, delete and sync with a 401, surfacing as "credentials
     * not found" rather than as a missing header.
     */
    private ExchangeFilterFunction internalAuth(InternalApiProperties internalApi) {
        return (request, next) -> next.exchange(
                ClientRequest.from(request)
                        .header(InternalHeaders.API_KEY, internalApi.getApiKey())
                        .header(InternalHeaders.CALLER_SERVICE, InternalHeaders.THIS_SERVICE)
                        .build());
    }

    /**
     * Propagates the inbound trace id so one logical operation carries the
     * same id across template-service, waba-service and storage-service.
     * Without it each service mints its own and a distributed trace cannot be
     * reassembled.
     *
     * <p>Reads the MDC rather than a method parameter because the id is
     * request-scoped context, not an argument any call site should have to
     * thread through. Sync work runs on the media-sync pool where the MDC is
     * not inherited, so the header is simply omitted there rather than sent
     * blank.
     */
    private ExchangeFilterFunction correlation() {
        return (request, next) -> {
            String traceId = MDC.get(LogKeys.TRACE_ID);
            if (traceId == null || traceId.isBlank()) {
                return next.exchange(request);
            }
            return next.exchange(ClientRequest.from(request)
                    .header(ApiHeaders.REQUEST_ID, traceId)
                    .build());
        };
    }

    /**
     * One log line per non-2xx upstream response, naming which upstream.
     *
     * <p>Adapters already catch and translate failures, but they do so after
     * the fact and cannot say which client produced it. Logging at the filter
     * means "storage-service returned 413" is visible even when the adapter
     * chooses to degrade gracefully and return {@code null}.
     */
    private ExchangeFilterFunction logFailures(String clientName) {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (response.statusCode().isError()) {
                log.warn("Upstream {} responded {} ", clientName, response.statusCode());
            }
            return Mono.just(response);
        });
    }
}
