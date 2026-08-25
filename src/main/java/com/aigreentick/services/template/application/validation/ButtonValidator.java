package com.aigreentick.services.template.application.validation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.request.*;
import com.aigreentick.services.template.application.validation.support.Placeholders;
import com.aigreentick.services.template.domain.enums.ButtonType;
import com.aigreentick.services.template.domain.enums.ComponentType;

/**
 * Button counts, grouping, and type/field coherence.
 *
 * Category-specific button restrictions (OTP only on AUTHENTICATION, etc.)
 * live in CategoryValidator — this class covers rules that hold everywhere.
 */
@Component
public class ButtonValidator {

    private static final int MAX_TOTAL = 10;
    private static final int MAX_PHONE = 1;
    private static final int MAX_URL = 2;
    private static final int TEXT_MAX = 25;
    private static final int URL_MAX = 2000;
    private static final String E164 = "^\\+?[1-9]\\d{1,17}$";

    public void validate(BaseTemplateRequestDto template, List<Violation> violations) {
        List<WhatsappTemplateComponentRequestDto> components = template.getComponents();
        if (components == null) return;

        for (int i = 0; i < components.size(); i++) {
            WhatsappTemplateComponentRequestDto c = components.get(i);
            if (c == null || c.getType() != ComponentType.BUTTONS) continue;

            List<WhatsappTemplateButtonRequestDto> buttons = c.getButtons();
            String field = Violation.componentPath(i, "buttons");

            if (buttons == null || buttons.isEmpty()) {
                violations.add(Violation.of(field, "META_BUTTONS_EMPTY",
                        "A BUTTONS component must contain at least one button"));
                continue;
            }

            validateCounts(buttons, field, violations);

            for (int j = 0; j < buttons.size(); j++) {
                validateButton(buttons.get(j), field + "[" + j + "]", violations);
            }
        }
    }

    private void validateCounts(List<WhatsappTemplateButtonRequestDto> buttons,
                                String field, List<Violation> violations) {

        if (buttons.size() > MAX_TOTAL) {
            violations.add(Violation.of(field, "META_BUTTONS_TOO_MANY",
                    "A template may have at most " + MAX_TOTAL + " buttons"));
        }

        long phone = buttons.stream().filter(b -> b != null && b.getType() == ButtonType.PHONE_NUMBER).count();
        if (phone > MAX_PHONE) {
            violations.add(Violation.of(field, "META_BUTTON_PHONE_LIMIT",
                    "At most " + MAX_PHONE + " PHONE_NUMBER button is allowed"));
        }

        long url = buttons.stream().filter(b -> b != null && b.getType() == ButtonType.URL).count();
        if (url > MAX_URL) {
            violations.add(Violation.of(field, "META_BUTTON_URL_LIMIT",
                    "At most " + MAX_URL + " URL buttons are allowed"));
        }

        if (!quickRepliesContiguous(buttons)) {
            violations.add(Violation.of(field, "META_QUICK_REPLY_NOT_GROUPED",
                    "QUICK_REPLY buttons must be grouped together, not interleaved with other types"));
        }
    }

    /** Meta requires quick replies to form one unbroken run. */
    private boolean quickRepliesContiguous(List<WhatsappTemplateButtonRequestDto> buttons) {
        boolean seen = false;
        boolean ended = false;
        for (WhatsappTemplateButtonRequestDto b : buttons) {
            if (b == null) continue;
            if (b.getType() == ButtonType.QUICK_REPLY) {
                if (ended) return false;
                seen = true;
            } else if (seen) {
                ended = true;
            }
        }
        return true;
    }

    private void validateButton(WhatsappTemplateButtonRequestDto b, String field,
                                List<Violation> violations) {
        if (b == null || b.getType() == null) return;

        if (b.getText() == null || b.getText().isBlank()) {
            violations.add(Violation.of(field + ".text", "META_BUTTON_TEXT_REQUIRED",
                    "Button label is required"));
        } else if (b.getText().length() > TEXT_MAX) {
            violations.add(Violation.of(field + ".text", "META_BUTTON_TEXT_TOO_LONG",
                    "Button label must not exceed " + TEXT_MAX + " characters"));
        }

        switch (b.getType()) {
            case URL -> validateUrl(b, field, violations);
            case PHONE_NUMBER -> validatePhone(b, field, violations);
            case QUICK_REPLY -> {
                if (b.getUrl() != null || b.getPhoneNumber() != null) {
                    violations.add(Violation.of(field, "META_QUICK_REPLY_EXTRA_FIELDS",
                            "QUICK_REPLY buttons must not carry a url or phoneNumber"));
                }
            }
            default -> { /* OTP / COPY_CODE / CATALOG / MPM / SPM — see CategoryValidator */ }
        }
    }

    private void validateUrl(WhatsappTemplateButtonRequestDto b, String field,
                             List<Violation> violations) {
        String url = b.getUrl();

        if (url == null || url.isBlank()) {
            violations.add(Violation.of(field + ".url", "META_BUTTON_URL_REQUIRED",
                    "URL button requires a url"));
            return;
        }
        if (url.length() > URL_MAX) {
            violations.add(Violation.of(field + ".url", "META_BUTTON_URL_TOO_LONG",
                    "Button url must not exceed " + URL_MAX + " characters"));
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            violations.add(Violation.of(field + ".url", "META_BUTTON_URL_SCHEME",
                    "Button url must start with http:// or https://"));
        }

        int variables = Placeholders.tokens(url).size();
        if (variables > 1) {
            violations.add(Violation.of(field + ".url", "META_BUTTON_URL_TOO_MANY_VARIABLES",
                    "A URL button may contain at most one variable"));
        } else if (variables == 1 && !Placeholders.endsWithPlaceholder(url)) {
            violations.add(Violation.of(field + ".url", "META_BUTTON_URL_VARIABLE_POSITION",
                    "A URL variable must appear at the end of the url"));
        }
    }

    private void validatePhone(WhatsappTemplateButtonRequestDto b, String field,
                               List<Violation> violations) {
        String phone = b.getPhoneNumber();

        if (phone == null || phone.isBlank()) {
            violations.add(Violation.of(field + ".phoneNumber", "META_BUTTON_PHONE_REQUIRED",
                    "PHONE_NUMBER button requires a phoneNumber"));
        } else if (!phone.matches(E164)) {
            violations.add(Violation.of(field + ".phoneNumber", "META_BUTTON_PHONE_FORMAT",
                    "phoneNumber must be in E.164 format, e.g. +919876543210"));
        }
    }
}