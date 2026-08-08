package com.aigreentick.services.template.domain.enums;

import java.util.Locale;

public enum TemplateQualityRating {

    GREEN,
    YELLOW,
    RED,
    UNKNOWN;


    public static TemplateQualityRating fromApi(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        try {
            return TemplateQualityRating.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
