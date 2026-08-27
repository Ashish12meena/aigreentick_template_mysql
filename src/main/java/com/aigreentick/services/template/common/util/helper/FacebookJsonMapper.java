package com.aigreentick.services.template.common.util.helper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * A snake_case {@link ObjectMapper} for parsing Meta Graph API payloads.
 *
 * <h2>Why this is a static constant and NOT a Spring bean</h2>
 *
 * It must not be a bean. {@code JacksonAutoConfiguration} declares its mapper
 * as:
 *
 * <pre>
 * &#64;Bean &#64;Primary &#64;ConditionalOnMissingBean
 * ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder)
 * </pre>
 *
 * <p>{@code @ConditionalOnMissingBean} matches on TYPE. Registering an
 * {@code ObjectMapper} bean here under any name, primary or not, makes Boot
 * back off completely: its mapper is never built, every {@code spring.jackson}
 * property stops applying, and {@code MappingJackson2HttpMessageConverter}
 * falls back to whatever single mapper is in the context — this one. The whole
 * public API would then serialize through the Meta mapper.
 *
 * <p>That failure is quiet and easy to misread. The symptoms are snake_case
 * responses that ignore the yaml setting, {@code LocalDateTime} rendered as
 * {@code [2026,8,27,22,40,27]} instead of ISO-8601 (Boot disables
 * {@code WRITE_DATES_AS_TIMESTAMPS}; a hand-built mapper does not), and null
 * fields appearing despite {@code default-property-inclusion: non_null}.
 * Those three together are the fingerprint of the auto-configuration having
 * backed off.
 *
 * <p>{@code WebClientConfig} avoids the same trap by building its camelCase
 * mappers as local variables inside {@code applyCamelCaseCodecs(...)}. This
 * class follows the same rule, and the same one {@link JsonHelper} already
 * follows: mappers with a specific job are held privately, never published to
 * the context.
 *
 * <h2>Why snake_case at all</h2>
 *
 * Meta serializes snake_case — {@code header_handle}, {@code previous_category},
 * {@code parameter_format}, {@code add_security_recommendation}. The DTOs it
 * binds into declare camelCase fields with no per-field {@code @JsonProperty},
 * so correct binding depends entirely on this naming strategy.
 *
 * <p>Parsing used to borrow the context mapper and therefore depended on
 * {@code spring.jackson.property-naming-strategy}, a setting whose real job is
 * this service's own API contract. When that was switched to camelCase,
 * {@code example.header_handle} bound to null — {@code fail-on-unknown-properties}
 * is off, so it was dropped rather than rejected — {@code setMediaHandle(...)}
 * was never called, {@code MediaSyncService} collected no tasks, and
 * {@code InternalMediaAdapter.uploadBatch()} was never reached. Sync reported
 * success with every template missing its media.
 */
public final class FacebookJsonMapper {

    private FacebookJsonMapper() {
    }

    /**
     * Meta adds fields to template payloads without notice, so unknown
     * properties are ignored rather than fatal — a new field upstream must not
     * fail an otherwise valid sync.
     *
     * <p>Explicit {@code @JsonProperty} names are not rewritten by the naming
     * strategy ({@code ALLOW_EXPLICIT_PROPERTY_RENAMING} is off by default), so
     * {@code @JsonProperty("id")} on {@code metaTemplateId} still binds Meta's
     * {@code id} rather than becoming {@code _id}.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * The mapper for Meta Graph API payloads.
     *
     * <p>Do not hand this to anything that serializes an HTTP response, and do
     * not register the returned instance as a bean.
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}