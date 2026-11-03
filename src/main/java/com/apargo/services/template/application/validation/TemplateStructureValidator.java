package com.apargo.services.template.application.validation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.apargo.services.template.api.request.*;
import com.apargo.services.template.application.validation.support.ProductCarousels;
import com.apargo.services.template.domain.enums.ButtonType;
import com.apargo.services.template.domain.enums.CardComponentFormat;
import com.apargo.services.template.domain.enums.CardComponentType;
import com.apargo.services.template.domain.enums.ComponentFormat;
import com.apargo.services.template.domain.enums.ComponentType;
import com.apargo.services.template.domain.enums.TemplateCategory;

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
    private static final int PRODUCT_CAROUSEL_CARDS = 2;
    private static final int CARD_BUTTON_TEXT_MAX = 25;

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
                case BODY -> validateBody(c, i, template.getCategory(), violations);
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

    private void validateBody(WhatsappTemplateComponentRequestDto c, int i, TemplateCategory category,
                              List<Violation> violations) {
        String text = c.getText();

        if (text == null || text.isBlank()) {
            if (category == TemplateCategory.AUTHENTICATION) {
                return;   // Meta generates the body; CategoryValidator forbids custom text
            }
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
        if (ProductCarousels.isProductCarousel(c)) {
            validateProductCarousel(cards, field, violations);
        } else {
            if (cards.size() > CAROUSEL_MAX_CARDS) {
                violations.add(Violation.of(field, "META_CAROUSEL_TOO_MANY_CARDS",
                        "A CAROUSEL may contain at most " + CAROUSEL_MAX_CARDS + " cards"));
            }
            rejectProductButtons(cards, field, violations);
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

    /**
     * A product card carousel. Meta's shape: exactly {@value #PRODUCT_CAROUSEL_CARDS} cards on create
     * (an approved template is then sent with up to ten), each a PRODUCT header and one SPM ("View")
     * or URL button, no card body. The product itself is chosen per send, so the header carries no
     * media, text or example.
     */
    private void validateProductCarousel(List<WhatsappTemplateCarouselCardRequestDto> cards, String field,
                                         List<Violation> violations) {
        if (cards.size() != PRODUCT_CAROUSEL_CARDS) {
            violations.add(Violation.of(field, "META_PRODUCT_CAROUSEL_TWO_CARDS",
                    "A product card carousel is created with exactly " + PRODUCT_CAROUSEL_CARDS
                            + " cards; up to 10 products are chosen when it is sent"));
        }

        for (int j = 0; j < cards.size(); j++) {
            WhatsappTemplateCarouselCardRequestDto card = cards.get(j);
            String cardField = field + "[" + j + "]";

            if (!ProductCarousels.hasProductHeader(card)) {
                violations.add(Violation.of(cardField, "META_CAROUSEL_MIXED_HEADERS",
                        "Every card of a product carousel needs a PRODUCT header; "
                                + "PRODUCT and media cards cannot be mixed"));
            }

            int buttonComponents = 0;
            List<WhatsappTemplateCarouseCardComponentRequestDto> components =
                    card == null || card.getComponents() == null ? List.of() : card.getComponents();
            for (WhatsappTemplateCarouseCardComponentRequestDto comp : components) {
                if (comp == null || comp.getType() == null) continue;
                switch (comp.getType()) {
                    case BODY -> violations.add(Violation.of(cardField, "META_PRODUCT_CARD_NO_BODY",
                            "A product card has no body; Meta shows the product's title and price"));
                    case HEADER -> {
                        boolean hasMedia = comp.getExample() != null || (comp.getText() != null && !comp.getText().isBlank());
                        if (comp.getFormat() == CardComponentFormat.PRODUCT && hasMedia) {
                            violations.add(Violation.of(cardField, "META_PRODUCT_CARD_HEADER_NO_MEDIA",
                                    "A PRODUCT header carries no text, media or example"));
                        }
                    }
                    case BUTTONS -> {
                        buttonComponents++;
                        validateProductCardButtons(comp.getButtons(), cardField, violations);
                    }
                }
            }
            if (buttonComponents != 1) {
                violations.add(Violation.of(cardField, "META_PRODUCT_CARD_ONE_BUTTON",
                        "A product card needs exactly one BUTTONS component with one SPM or URL button"));
            }
        }
    }

    private void validateProductCardButtons(List<WhatsappTemplateCarouselButtonRequestDto> buttons, String cardField,
                                            List<Violation> violations) {
        if (buttons == null || buttons.size() != 1) {
            violations.add(Violation.of(cardField + ".buttons", "META_PRODUCT_CARD_ONE_BUTTON",
                    "A product card needs exactly one SPM or URL button"));
            return;
        }
        WhatsappTemplateCarouselButtonRequestDto button = buttons.get(0);
        if (button == null || (button.getType() != ButtonType.SPM && button.getType() != ButtonType.URL)) {
            violations.add(Violation.of(cardField + ".buttons[0].type", "META_PRODUCT_CARD_BUTTON_TYPE",
                    "A product card's button is SPM (\"View\") or URL"));
            return;
        }
        // The column is NOT NULL, and Meta labels the button with it.
        if (button.getText() == null || button.getText().isBlank()) {
            violations.add(Violation.of(cardField + ".buttons[0].text", "META_BUTTON_TEXT_REQUIRED",
                    "Button label is required"));
        } else if (button.getText().length() > CARD_BUTTON_TEXT_MAX) {
            violations.add(Violation.of(cardField + ".buttons[0].text", "META_BUTTON_TEXT_TOO_LONG",
                    "Button label must not exceed " + CARD_BUTTON_TEXT_MAX + " characters"));
        }
    }

    /** SPM opens a card's product, so it exists only on product cards. */
    private void rejectProductButtons(List<WhatsappTemplateCarouselCardRequestDto> cards, String field,
                                      List<Violation> violations) {
        for (int j = 0; j < cards.size(); j++) {
            WhatsappTemplateCarouselCardRequestDto card = cards.get(j);
            if (card == null || card.getComponents() == null) continue;
            for (WhatsappTemplateCarouseCardComponentRequestDto comp : card.getComponents()) {
                if (comp == null || comp.getButtons() == null) continue;
                boolean spm = comp.getButtons().stream().anyMatch(b -> b != null && b.getType() == ButtonType.SPM);
                if (spm) {
                    violations.add(Violation.of(field + "[" + j + "]", "META_SPM_PRODUCT_CARD_ONLY",
                            "An SPM button is only allowed on a product card (PRODUCT header)"));
                }
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