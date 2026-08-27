package com.aigreentick.services.template.api.request;

import java.util.List;

import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * A template as returned by the Meta Graph API.
 *
 * <p>Despite living in {@code api.request}, this is NOT bound from an inbound
 * HTTP request — nothing accepts it as a {@code @RequestBody}. It is populated
 * only by {@code SyncTemplateFromFacebookUseCaseImpl} from Meta's response,
 * using the snake_case mapper in {@code FacebookJacksonConfig}. The camelCase
 * fields below therefore rely on that mapper's naming strategy, not on
 * {@code spring.jackson.property-naming-strategy}.
 *
 * <p>The nested {@code WhatsappTemplateComponentRequestDto} and
 * {@code WhatsappTemplateExampleRequestDto} ARE shared with
 * {@code CreateTemplateRequestDto} and are bound from real request bodies, so
 * they must not be annotated with a class-level naming strategy.
 */
@Data
public class SyncTemplateRequest {

    private String name;

    private String category;

    private String language;

    private TemplateStatus status; // PENDING, APPROVED, REJECTED

    /**
     * Meta's field is {@code rejected_reason}, not {@code rejection_reason},
     * so the snake_case strategy alone would derive the wrong name and this
     * would bind null on every rejected template. Named explicitly.
     */
    @JsonProperty("rejected_reason")
    private String rejectionReason;

    private String previousCategory;

    private String parameterFormat;

    /**
     * Explicit names are not rewritten by the naming strategy
     * ({@code ALLOW_EXPLICIT_PROPERTY_RENAMING} is off by default), so this
     * binds Meta's {@code id} rather than {@code _id}.
     */
    @JsonProperty("id")
    private String metaTemplateId;

    private List<WhatsappTemplateComponentRequestDto> components;
}