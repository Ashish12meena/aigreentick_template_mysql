package com.apargo.services.template.common.util.helper;

import com.apargo.services.template.api.request.BaseTemplateRequestDto;
import com.apargo.services.template.domain.enums.ButtonType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Writes a template request as the JSON body Meta's create / edit endpoints
 * expect: snake_case, nulls omitted, plus the one place where Meta's shape
 * differs from our DTO.
 *
 * <p>A {@code COPY_CODE} (coupon) button's {@code example} is a single string
 * on Meta ({@code "example": "SAVE20"}), while every other button takes an
 * array. The DTO keeps a list for all buttons, so it is unwrapped here.
 */
public final class MetaTemplateJson {

    private static final ObjectMapper SNAKE_CASE_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private MetaTemplateJson() {
    }

    /** Body for create: the whole template. */
    public static String write(BaseTemplateRequestDto template) {
        return toJson(toTree(template));
    }

    /**
     * Body for edit: {@code components}, plus {@code category} when
     * {@code includeCategory} (Meta refuses a category change on an approved
     * template). Name and language cannot be edited, so they are not sent.
     */
    public static String writeEdit(BaseTemplateRequestDto template, boolean includeCategory) {
        ObjectNode root = toTree(template);
        root.remove("name");
        root.remove("language");
        if (!includeCategory) {
            root.remove("category");
        }
        return toJson(root);
    }

    private static ObjectNode toTree(BaseTemplateRequestDto template) {
        ObjectNode root = SNAKE_CASE_MAPPER.valueToTree(template);
        for (JsonNode component : root.path("components")) {
            for (JsonNode button : component.path("buttons")) {
                unwrapCopyCodeExample(button);
            }
        }
        return root;
    }

    private static String toJson(JsonNode root) {
        try {
            return SNAKE_CASE_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Meta template JSON serialization failed", e);
        }
    }

    private static void unwrapCopyCodeExample(JsonNode button) {
        if (!(button instanceof ObjectNode node)
                || !ButtonType.COPY_CODE.name().equals(node.path("type").asText())) {
            return;
        }
        JsonNode example = node.path("example");
        if (example.isArray() && !example.isEmpty()) {
            node.put("example", example.get(0).asText());
        }
    }
}
