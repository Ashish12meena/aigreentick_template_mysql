package com.aigreentick.services.template.application.validation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.request.*;
import com.aigreentick.services.template.domain.enums.CardComponentType;
import com.aigreentick.services.template.domain.enums.ComponentFormat;
import com.aigreentick.services.template.domain.enums.ComponentType;

/**
 * Component cardinality, text limits, header media, and carousel shape.
 */
@Component
public class TemplateStructureValidator {

    private static final int BODY_MAX = 1024;
    private static final int HEADER_MAX = 60;
    private static final int FOOTER_MAX = 60;
    private static final int CAROUSEL_MIN_CARDS = 1;
    private static final int CAROUSEL_MAX_CARDS = 10;

    public void validate(BaseTemplateRequestDto template, List<Violation> violations) {
        List<WhatsappTemplateComponentRequestDto> components = template.getComponents();
        if (components == null || components.isEmpty()) {
            return;   // @NotEmpty already reported it
        }

        validateCardinality(components, violations);

        for (int i = 0; i < components.size(); i++) {
            WhatsappTemplateComponentRequestDto c = components.get(i);
            if (c == null || c.getType() == null) continue;

            switch (c.getType()) {
                case BODY -> validateBody(c, i, violations);
                case HEADER -> validateHeader(c, i, violations);
                case FOOTER -> validateFooter(c, i, violations);
                case CAROUSEL -> validateCarousel(c, i, violations);
                case BUTTONS, LIMITED_TIME_OFFER -> { /* handled elsewhere */ }
            }
        }
    }

    // ── cardinality ──

    private void validateCardinality(List<WhatsappTemplateComponentRequestDto> components,
                                     List<Violation> violations) {

        Map<ComponentType, Integer> counts = new EnumMap<>(ComponentType.class);
        for (WhatsappTemplateComponentRequestDto c : components) {
            if (c != null && c.getType() != null) {
                counts.merge(c.getType(), 1, Integer::sum);
            }
        }

        int bodyCount = counts.getOrDefault(ComponentType.BODY, 0);
        if (bodyCount == 0) {
            violations.add(Violation.of("template.components", "META_BODY_REQUIRED",
                    "Template must contain exactly one BODY component"));
        } else if (bodyCount > 1) {
            violations.add(Violation.of("template.components", "META_BODY_DUPLICATED",
                    "Template must contain exactly one BODY component, found " + bodyCount));
        }

        for (ComponentType type : List.of(ComponentType.HEADER, ComponentType.FOOTER,
                ComponentType.BUTTONS, ComponentType.CAROUSEL, ComponentType.LIMITED_TIME_OFFER)) {

            int count = counts.getOrDefault(type, 0);
            if (count > 1) {
                violations.add(Violation.of("template.components", "META_COMPONENT_DUPLICATED",
                        "At most one " + type + " component is allowed, found " + count));
            }
        }
    }

    // ── per-component ──

    private void validateBody(WhatsappTemplateComponentRequestDto c, int i, List<Violation> violations) {
        String text = c.getText();

        if (text == null || text.isBlank()) {
            violations.add(Violation.of(Violation.componentPath(i, "text"),
                    "META_BODY_TEXT_REQUIRED", "BODY text is required"));
        } else if (text.length() > BODY_MAX) {
            violations.add(Violation.of(Violation.componentPath(i, "text"),
                    "META_BODY_TOO_LONG",
                    "BODY text must not exceed " + BODY_MAX + " characters"));
        }
    }

    private void validateHeader(WhatsappTemplateComponentRequestDto c, int i, List<Violation> violations) {
        ComponentFormat format = c.getFormat() == null ? ComponentFormat.TEXT : c.getFormat();
        WhatsappTemplateExampleRequestDto example = c.getExample();
        boolean hasHandle = example != null
                && example.getHeaderHandle() != null
                && !example.getHeaderHandle().isEmpty();
        String text = c.getText();
        boolean hasText = text != null && !text.isBlank();

        switch (format) {
            case TEXT -> {
                if (!hasText) {
                    violations.add(Violation.of(Violation.componentPath(i, "text"),
                            "META_TEXT_HEADER_REQUIRES_TEXT", "A TEXT header requires text"));
                } else if (text.length() > HEADER_MAX) {
                    violations.add(Violation.of(Violation.componentPath(i, "text"),
                            "META_HEADER_TOO_LONG",
                            "HEADER text must not exceed " + HEADER_MAX + " characters"));
                }
                if (hasHandle) {
                    violations.add(Violation.of(Violation.componentPath(i, "example.headerHandle"),
                            "META_TEXT_HEADER_NO_HANDLE",
                            "A TEXT header must not carry a media handle"));
                }
            }
            case IMAGE, VIDEO, DOCUMENT -> {
                if (!hasHandle) {
                    violations.add(Violation.of(Violation.componentPath(i, "example.headerHandle"),
                            "META_MEDIA_HEADER_HANDLE_REQUIRED",
                            format + " header requires a media handle from the Resumable Upload API"));
                }
                if (hasText) {
                    violations.add(Violation.of(Violation.componentPath(i, "text"),
                            "META_MEDIA_HEADER_NO_TEXT", format + " header must not carry text"));
                }
            }
            case LOCATION -> {
                if (hasText) {
                    violations.add(Violation.of(Violation.componentPath(i, "text"),
                            "META_LOCATION_HEADER_NO_TEXT", "LOCATION header must not carry text"));
                }
            }
            case PRODUCT -> { /* catalog availability is validated by Meta */ }
        }
    }

    private void validateFooter(WhatsappTemplateComponentRequestDto c, int i, List<Violation> violations) {
        String text = c.getText();
        if (text == null) return;

        if (text.length() > FOOTER_MAX) {
            violations.add(Violation.of(Violation.componentPath(i, "text"),
                    "META_FOOTER_TOO_LONG",
                    "FOOTER text must not exceed " + FOOTER_MAX + " characters"));
        }
        if (text.contains("{{")) {
            violations.add(Violation.of(Violation.componentPath(i, "text"),
                    "META_FOOTER_NO_VARIABLES", "FOOTER must not contain variables"));
        }
    }

    // ── carousel ──

    private void validateCarousel(WhatsappTemplateComponentRequestDto c, int i, List<Violation> violations) {
        List<WhatsappTemplateCarouselCardRequestDto> cards = c.getCards();
        String field = Violation.componentPath(i, "cards");

        if (cards == null || cards.size() < CAROUSEL_MIN_CARDS) {
            violations.add(Violation.of(field, "META_CAROUSEL_NO_CARDS",
                    "A CAROUSEL requires at least " + CAROUSEL_MIN_CARDS + " card"));
            return;
        }
        if (cards.size() > CAROUSEL_MAX_CARDS) {
            violations.add(Violation.of(field, "META_CAROUSEL_TOO_MANY_CARDS",
                    "A CAROUSEL may contain at most " + CAROUSEL_MAX_CARDS + " cards"));
        }

        // Meta rejects heterogeneous carousels: every card must share the same
        // component structure, media format and button layout.
        String reference = signature(cards.get(0));
        for (int j = 1; j < cards.size(); j++) {
            if (!signature(cards.get(j)).equals(reference)) {
                violations.add(Violation.of(field + "[" + j + "]",
                        "META_CAROUSEL_CARDS_NOT_UNIFORM",
                        "All carousel cards must share the same component structure, "
                                + "media format and button layout"));
            }
        }
    }

    private String signature(WhatsappTemplateCarouselCardRequestDto card) {
        if (card == null || card.getComponents() == null) return "";

        StringBuilder sb = new StringBuilder();
        for (WhatsappTemplateCarouseCardComponentRequestDto comp : card.getComponents()) {
            if (comp == null) continue;
            sb.append(comp.getType()).append(':').append(comp.getFormat()).append('|');
            if (comp.getType() == CardComponentType.BUTTONS && comp.getButtons() != null) {
                comp.getButtons().forEach(b -> sb.append(b.getType()).append(','));
            }
        }
        return sb.toString();
    }
}