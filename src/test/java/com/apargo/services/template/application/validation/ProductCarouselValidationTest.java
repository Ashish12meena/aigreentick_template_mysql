package com.apargo.services.template.application.validation;

import com.apargo.services.template.api.request.BaseTemplateRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateCarouseCardComponentRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateCarouselButtonRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateCarouselCardRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateComponentRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateExampleRequestDto;
import com.apargo.services.template.common.exception.TemplateRuleViolationException;
import com.apargo.services.template.domain.enums.ButtonType;
import com.apargo.services.template.domain.enums.CardComponentFormat;
import com.apargo.services.template.domain.enums.CardComponentType;
import com.apargo.services.template.domain.enums.ComponentType;
import com.apargo.services.template.domain.enums.TemplateCategory;
import org.junit.jupiter.api.Test;

import static com.apargo.services.template.ProductCarouselFixtures.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Product card carousels: Meta's shape is enforced, and media carousels keep their rules. */
class ProductCarouselValidationTest {

    private final TemplateValidationService validation = new TemplateValidationService(
            new TemplateIdentityValidator(), new TemplateStructureValidator(), new PlaceholderValidator(),
            new ButtonValidator(), new CategoryValidator());

    @Test
    void theMetaDocExampleIsValid() {
        assertThatCode(() -> validation.validate(template(TemplateCategory.MARKETING, productCard(), productCard())))
                .doesNotThrowAnyException();
    }

    @Test
    void aUrlButtonIsAllowedInsteadOfView() {
        WhatsappTemplateCarouselCardRequestDto card = card(header(CardComponentFormat.PRODUCT),
                buttons(button(ButtonType.URL, "Shop", "https://shop.example.com/p/{{1}}")));

        assertThatCode(() -> validation.validate(template(TemplateCategory.MARKETING, card, card)))
                .doesNotThrowAnyException();
    }

    @Test
    void threeCardsAreRejected() {
        assertThat(codes(template(TemplateCategory.MARKETING, productCard(), productCard(), productCard())))
                .contains("META_PRODUCT_CAROUSEL_TWO_CARDS");
    }

    @Test
    void oneCardIsRejected() {
        assertThat(codes(template(TemplateCategory.MARKETING, productCard())))
                .contains("META_PRODUCT_CAROUSEL_TWO_CARDS");
    }

    @Test
    void aCardBodyIsRejected() {
        WhatsappTemplateCarouseCardComponentRequestDto body = new WhatsappTemplateCarouseCardComponentRequestDto();
        body.setType(CardComponentType.BODY);
        body.setText("Great plant");
        WhatsappTemplateCarouselCardRequestDto card = card(header(CardComponentFormat.PRODUCT), body, viewButtons());

        assertThat(codes(template(TemplateCategory.MARKETING, card, card)))
                .contains("META_PRODUCT_CARD_NO_BODY");
    }

    @Test
    void mixingProductAndMediaCardsIsRejected() {
        WhatsappTemplateCarouselCardRequestDto image = card(mediaHeader(),
                buttons(button(ButtonType.QUICK_REPLY, "More", null)));

        assertThat(codes(template(TemplateCategory.MARKETING, productCard(), image)))
                .contains("META_CAROUSEL_MIXED_HEADERS");
    }

    @Test
    void anSpmButtonOnAMediaCardIsRejected() {
        WhatsappTemplateCarouselCardRequestDto image = card(mediaHeader(), buttons(button(ButtonType.SPM, "View", null)));

        assertThat(codes(template(TemplateCategory.MARKETING, image, image)))
                .contains("META_SPM_PRODUCT_CARD_ONLY");
    }

    @Test
    void aProductCarouselIsMarketingOnly() {
        assertThat(codes(template(TemplateCategory.UTILITY, productCard(), productCard())))
                .contains("META_PRODUCT_CAROUSEL_MARKETING_ONLY");
    }

    @Test
    void twoButtonsOnAProductCardAreRejected() {
        WhatsappTemplateCarouselCardRequestDto card = card(header(CardComponentFormat.PRODUCT),
                buttons(button(ButtonType.SPM, "View", null), button(ButtonType.URL, "Shop", "https://x.example.com")));

        assertThat(codes(template(TemplateCategory.MARKETING, card, card)))
                .contains("META_PRODUCT_CARD_ONE_BUTTON");
    }

    @Test
    void aQuickReplyOnAProductCardIsRejected() {
        WhatsappTemplateCarouselCardRequestDto card = card(header(CardComponentFormat.PRODUCT),
                buttons(button(ButtonType.QUICK_REPLY, "More", null)));

        assertThat(codes(template(TemplateCategory.MARKETING, card, card)))
                .contains("META_PRODUCT_CARD_BUTTON_TYPE");
    }

    @Test
    void aProductHeaderCarriesNoMedia() {
        WhatsappTemplateCarouseCardComponentRequestDto header = header(CardComponentFormat.PRODUCT);
        WhatsappTemplateExampleRequestDto example = new WhatsappTemplateExampleRequestDto();
        example.setHeaderHandle(List.of("4::aW1hZ2U="));
        header.setExample(example);
        WhatsappTemplateCarouselCardRequestDto card = card(header, viewButtons());

        assertThat(codes(template(TemplateCategory.MARKETING, card, card)))
                .contains("META_PRODUCT_CARD_HEADER_NO_MEDIA");
    }

    @Test
    void anUnlabelledViewButtonIsRejected() {
        WhatsappTemplateCarouselCardRequestDto card = card(header(CardComponentFormat.PRODUCT),
                buttons(button(ButtonType.SPM, null, null)));

        assertThat(codes(template(TemplateCategory.MARKETING, card, card)))
                .contains("META_BUTTON_TEXT_REQUIRED");
    }

    @Test
    void aMediaCarouselKeepsItsRules() {
        WhatsappTemplateCarouselCardRequestDto image = card(mediaHeader(), buttons(button(ButtonType.QUICK_REPLY, "More", null)));

        // Three media cards are fine (only product carousels are fixed at two) ...
        assertThatCode(() -> validation.validate(template(TemplateCategory.MARKETING, image, image, image)))
                .doesNotThrowAnyException();
        // ... and eleven are still too many.
        WhatsappTemplateCarouselCardRequestDto[] eleven = new WhatsappTemplateCarouselCardRequestDto[11];
        java.util.Arrays.fill(eleven, image);
        assertThat(codes(template(TemplateCategory.MARKETING, eleven))).contains("META_CAROUSEL_TOO_MANY_CARDS");
    }

    // ── builders ────────────────────────────────────────────────────────────

    private List<String> codes(BaseTemplateRequestDto template) {
        TemplateRuleViolationException e = catchThrowableOfType(TemplateRuleViolationException.class,
                () -> validation.validate(template));
        assertThat(e).as("expected a rule violation").isNotNull();
        return e.getViolations().stream().map(Violation::code).toList();
    }
}
