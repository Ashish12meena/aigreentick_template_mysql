package com.apargo.services.template.domain.enums;

/**
 * A carousel card's header format. {@code PRODUCT} makes a product card carousel: the card shows a
 * catalog product (image, title, price) chosen when the message is sent, so it carries no media.
 * Stored in a MySQL ENUM column — a new value needs an {@code ALTER TABLE} (db/migration).
 */
public enum CardComponentFormat {
    IMAGE, VIDEO, DOCUMENT, PRODUCT
}
