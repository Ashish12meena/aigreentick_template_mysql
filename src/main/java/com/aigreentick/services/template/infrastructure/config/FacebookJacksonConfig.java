package com.aigreentick.services.template.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A snake_case {@link ObjectMapper} dedicated to parsing Meta Graph API
 * payloads.
 *
 * <h2>Why this exists</h2>
 *
 * The Meta Graph API serializes snake_case: {@code header_handle},
 * {@code previous_category}, {@code parameter_format},SyncTemplateFromFacebookUseCaseImpl
 * {@code add_security_recommendation}, {@code code_expiration_minutes}.
 * The DTOs it binds into ({@code SyncTemplateRequest} and the nested
 * {@code WhatsappTemplate*RequestDto} types) declare camelCase Java fields
 * with no per-field {@code @JsonProperty}, so correct binding depends
 * entirely on the mapper's naming strategy.
 *
 * <p>That strategy used to come from {@code spring.jackson
 * .property-naming-strategy: SNAKE_CASE}, because
 * {@code SyncTemplateFromFacebookUseCaseImpl} injected the auto-configured
 * context {@link ObjectMapper}. Meta parsing therefore silently depended on
 * a setting whose actual purpose is this service's own public API contract —
 * two unrelated concerns sharing one knob.
 *
 * <p>Changing that knob broke sync in a way that produced no error at all.
 * {@code fail-on-unknown-properties} is off, so a camelCase mapper does not
 * reject {@code header_handle}; it drops it. {@code example.headerHandle}
 * comes back null, {@code TemplateSyncMapper} never calls
 * {@code setMediaHandle(...)}, {@code MediaSyncService.collectMediaTasks()}
 * returns an empty list, and {@code InternalMediaAdapter.uploadBatch()} is
 * never reached. Templates sync successfully with no images and the only
 * trace is a DEBUG line reading "No media to resolve".
 *
 * <p>Pinning the strategy here makes the Meta contract explicit and
 * independent of {@code spring.jackson}, so the server-side naming strategy
 * can be changed freely without reaching into the sync path. This mirrors
 * what {@link WebClientConfig} already does for the outbound clients, which
 * pin camelCase for the same reason in the opposite direction.
 *
 * <h2>Why not annotate the DTOs instead</h2>
 *
 * {@code @JsonNaming} on {@code SyncTemplateRequest} would not propagate to
 * the nested component and example DTOs — a class-level naming strategy
 * applies only to the class carrying it. Annotating those nested types
 * directly is not an option either: they are shared with
 * {@code CreateTemplateRequestDto} and are bound from inbound API request
 * bodies, so forcing snake_case on them would override whatever the public
 * API contract is meant to be.
 *
 * <p>Note this mapper does NOT replace the context {@code ObjectMapper};
 * it is not {@code @Primary}. Injection sites must ask for it by qualifier.
 */
@Configuration
public class FacebookJacksonConfig {

    /** Bean name for the Meta Graph API (snake_case) mapper. */
    public static final String FACEBOOK_OBJECT_MAPPER = "facebookObjectMapper";

    /**
     * Meta adds fields to template payloads without notice, so unknown
     * properties are ignored rather than fatal — a new field upstream must
     * not fail an otherwise valid sync.
     *
     * <p>{@code findAndRegisterModules()} picks up JavaTimeModule and the
     * parameter-names module, matching how the outbound client mappers in
     * {@link WebClientConfig} are built.
     *
     * <p>Explicit {@code @JsonProperty} names are not affected by the naming
     * strategy ({@code ALLOW_EXPLICIT_PROPERTY_RENAMING} is off by default),
     * so {@code @JsonProperty("id")} on {@code metaTemplateId} keeps binding
     * Meta's {@code id} and is not rewritten to {@code _id}.
     */
    @Bean(FACEBOOK_OBJECT_MAPPER)
    public ObjectMapper facebookObjectMapper() {
        return new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}