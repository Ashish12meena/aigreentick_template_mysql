package com.apargo.services.template;

import com.apargo.services.template.api.request.BaseTemplateRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateCarouseCardComponentRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateCarouselButtonRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateCarouselCardRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateComponentRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateExampleRequestDto;
import com.apargo.services.template.domain.enums.ButtonType;
import com.apargo.services.template.domain.enums.CardComponentFormat;
import com.apargo.services.template.domain.enums.CardComponentType;
import com.apargo.services.template.domain.enums.ComponentType;
import com.apargo.services.template.domain.enums.TemplateCategory;

import java.util.ArrayList;
import java.util.List;

/** Request builders for product card carousel templates, shaped like Meta's doc example. */
public final class ProductCarouselFixtures {

    private ProductCarouselFixtures() {
    }

    public static BaseTemplateRequestDto template(TemplateCategory category, WhatsappTemplateCarouselCardRequestDto... cards) {
        WhatsappTemplateComponentRequestDto body = new WhatsappTemplateComponentRequestDto();
        body.setType(ComponentType.BODY);
        body.setText("Rare succulents for sale! Tap a plant to see it.");

        WhatsappTemplateComponentRequestDto carousel = new WhatsappTemplateComponentRequestDto();
        carousel.setType(ComponentType.CAROUSEL);
        carousel.setCards(new ArrayList<>(List.of(cards)));

        BaseTemplateRequestDto template = new BaseTemplateRequestDto();
        template.setName("carousel_template_product_cards_v1");
        template.setLanguage("en_US");
        template.setCategory(category);
        template.setComponents(new ArrayList<>(List.of(body, carousel)));
        return template;
    }

    public static WhatsappTemplateCarouselCardRequestDto productCard() {
        return card(header(CardComponentFormat.PRODUCT), viewButtons());
    }

    public static WhatsappTemplateCarouselCardRequestDto card(WhatsappTemplateCarouseCardComponentRequestDto... components) {
        WhatsappTemplateCarouselCardRequestDto card = new WhatsappTemplateCarouselCardRequestDto();
        card.setComponents(new ArrayList<>(List.of(components)));
        return card;
    }

    public static WhatsappTemplateCarouseCardComponentRequestDto header(CardComponentFormat format) {
        WhatsappTemplateCarouseCardComponentRequestDto header = new WhatsappTemplateCarouseCardComponentRequestDto();
        header.setType(CardComponentType.HEADER);
        header.setFormat(format);
        return header;
    }

    public static WhatsappTemplateCarouseCardComponentRequestDto mediaHeader() {
        WhatsappTemplateCarouseCardComponentRequestDto header = header(CardComponentFormat.IMAGE);
        WhatsappTemplateExampleRequestDto example = new WhatsappTemplateExampleRequestDto();
        example.setHeaderHandle(List.of("4::aW1hZ2U="));
        header.setExample(example);
        return header;
    }

    public static WhatsappTemplateCarouseCardComponentRequestDto viewButtons() {
        return buttons(button(ButtonType.SPM, "View", null));
    }

    public static WhatsappTemplateCarouseCardComponentRequestDto buttons(WhatsappTemplateCarouselButtonRequestDto... buttons) {
        WhatsappTemplateCarouseCardComponentRequestDto component = new WhatsappTemplateCarouseCardComponentRequestDto();
        component.setType(CardComponentType.BUTTONS);
        component.setButtons(new ArrayList<>(List.of(buttons)));
        return component;
    }

    public static WhatsappTemplateCarouselButtonRequestDto button(ButtonType type, String text, String url) {
        WhatsappTemplateCarouselButtonRequestDto button = new WhatsappTemplateCarouselButtonRequestDto();
        button.setType(type);
        button.setText(text);
        button.setUrl(url);
        if (url != null && url.contains("{{1}}")) {
            button.setExample(List.of("https://shop.example.com/p/123"));
        }
        return button;
    }
}
