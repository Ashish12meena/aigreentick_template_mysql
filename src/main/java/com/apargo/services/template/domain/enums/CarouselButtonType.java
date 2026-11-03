package com.apargo.services.template.domain.enums;

import java.util.Optional;

/**
 * A carousel card's button type. {@code SPM} is the "View" button of a product card carousel; it
 * opens the card's product. Stored in a MySQL ENUM column — a new value needs an
 * {@code ALTER TABLE} (db/migration).
 */
public enum CarouselButtonType {
    URL, QUICK_REPLY, PHONE_NUMBER, SPM;

    /** The carousel type of a template button type; empty when a card cannot carry it. */
    public static Optional<CarouselButtonType> of(ButtonType type) {
        if (type == null) {
            return Optional.empty();
        }
        for (CarouselButtonType value : values()) {
            if (value.name().equals(type.name())) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
