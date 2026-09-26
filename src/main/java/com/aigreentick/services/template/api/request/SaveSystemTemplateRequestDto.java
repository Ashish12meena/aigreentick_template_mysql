package com.aigreentick.services.template.api.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body for creating or replacing a Template Library entry
 * ({@code POST} / {@code PUT /internal/v1/template-library}).
 *
 * <p>{@code template} and {@code variables} are exactly the create-template
 * request's fields; they are stored as-is and returned as-is, so the frontend
 * can pass a library template straight to {@code POST /api/v1/templates}.
 * The same {@code @Valid} cascade as {@link CreateTemplateRequestDto} applies.
 */
@Data
public class SaveSystemTemplateRequestDto {

    @Size(max = 500, message = "description must not exceed 500 characters")
    private String description;

    /**
     * Public http(s) URL of a sample header media file on our own storage, for
     * the library preview only. Not a Meta handle and never sent to Meta.
     */
    @Size(max = 500, message = "sampleMediaUrl must not exceed 500 characters")
    @Pattern(regexp = "^https?://\\S+$", message = "sampleMediaUrl must be an http(s) URL")
    private String sampleMediaUrl;

    @NotNull(message = "template is required")
    @Valid
    private BaseTemplateRequestDto template;

    private List<@Valid @NotNull(message = "variable entry must not be null") WhatsappTemplateVariablesRequestDto> variables;

    /** Inactive entries are hidden from the public library. JSON property: {@code active}. */
    private boolean active = true;
}
