package com.apargo.services.template.application.validation.support;

import java.util.List;

import com.apargo.services.template.api.request.WhatsappTemplateCarouseCardComponentRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateCarouselCardRequestDto;
import com.apargo.services.template.api.request.WhatsappTemplateComponentRequestDto;
import com.apargo.services.template.domain.enums.CardComponentFormat;
import com.apargo.services.template.domain.enums.CardComponentType;
import com.apargo.services.template.domain.enums.ComponentType;

/**
 * Recognises a product card carousel: a CAROUSEL whose cards show catalog products
 * ({@code HEADER} format {@code PRODUCT}) rather than uploaded media. Meta's rules for it differ from
 * a media carousel's — exactly two cards on create, no card body, one {@code SPM} or URL button —
 * so the validators branch on this.
 */
public final class ProductCarousels {

    private ProductCarousels() {}

    /** True when any card of this CAROUSEL has a PRODUCT header. */
    public static boolean isProductCarousel(WhatsappTemplateComponentRequestDto component) {
        if (component == null || component.getType() != ComponentType.CAROUSEL || component.getCards() == null) {
            return false;
        }
        return component.getCards().stream().anyMatch(ProductCarousels::hasProductHeader);
    }

    public static boolean hasProductHeader(WhatsappTemplateCarouselCardRequestDto card) {
        return header(card) != null && header(card).getFormat() == CardComponentFormat.PRODUCT;
    }

    /** The card's first HEADER component, or null. */
    public static WhatsappTemplateCarouseCardComponentRequestDto header(WhatsappTemplateCarouselCardRequestDto card) {
        if (card == null || card.getComponents() == null) {
            return null;
        }
        List<WhatsappTemplateCarouseCardComponentRequestDto> components = card.getComponents();
        for (WhatsappTemplateCarouseCardComponentRequestDto component : components) {
            if (component != null && component.getType() == CardComponentType.HEADER) {
                return component;
            }
        }
        return null;
    }
}
