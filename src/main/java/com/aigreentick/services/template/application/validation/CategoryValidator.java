package com.aigreentick.services.template.application.validation;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.dto.request.create.*;
import com.aigreentick.services.template.domain.enums.*;

/**
 * Category-specific rules. AUTHENTICATION is by far the most restrictive and
 * the most commonly violated: Meta generates the body copy itself, allows only
 * OTP buttons, and forbids media headers and custom footers.
 */
@Component
public class CategoryValidator {

    private static final Set<ButtonType> OTP_BUTTONS =
            EnumSet.of(ButtonType.OTP, ButtonType.COPY_CODE);
    private static final Set<ButtonType> COMMERCE_BUTTONS =
            EnumSet.of(ButtonType.CATALOG, ButtonType.MPM, ButtonType.SPM);
    private static final int EXPIRY_MIN = 1;
    private static final int EXPIRY_MAX = 90;

    public void validate(BaseTemplateRequestDto template, List<Violation> violations) {
        TemplateCategory category = template.getCategory();
        List<WhatsappTemplateComponentRequestDto> components = template.getComponents();
        if (category == null || components == null) return;

        switch (category) {
            case AUTHENTICATION -> validateAuthentication(components, violations);
            case MARKETING -> validateMarketing(components, violations);
            case UTILITY -> validateUtility(components, violations);
        }
    }

    // ── AUTHENTICATION ──

    private void validateAuthentication(List<WhatsappTemplateComponentRequestDto> components,
                                        List<Violation> violations) {

        for (int i = 0; i < components.size(); i++) {
            WhatsappTemplateComponentRequestDto c = components.get(i);
            if (c == null || c.getType() == null) continue;

            switch (c.getType()) {
                case BODY -> {
                    if (c.getText() != null && !c.getText().isBlank()) {
                        violations.add(Violation.of(Violation.componentPath(i, "text"),
                                "META_AUTH_BODY_NOT_CUSTOMISABLE",
                                "AUTHENTICATION templates use Meta-generated body copy; "
                                        + "custom text is not allowed"));
                    }
                    Integer expiry = c.getCodeExpirationMinutes();
                    if (expiry != null && (expiry < EXPIRY_MIN || expiry > EXPIRY_MAX)) {
                        violations.add(Violation.of(
                                Violation.componentPath(i, "codeExpirationMinutes"),
                                "META_AUTH_EXPIRY_OUT_OF_RANGE",
                                "codeExpirationMinutes must be between "
                                        + EXPIRY_MIN + " and " + EXPIRY_MAX));
                    }
                }
                case HEADER -> {
                    if (c.getFormat() != null && c.getFormat() != ComponentFormat.TEXT) {
                        violations.add(Violation.of(Violation.componentPath(i, "format"),
                                "META_AUTH_NO_MEDIA_HEADER",
                                "AUTHENTICATION templates cannot have a media header"));
                    }
                }
                case FOOTER -> violations.add(Violation.of(Violation.componentPath(i, "text"),
                        "META_AUTH_NO_CUSTOM_FOOTER",
                        "AUTHENTICATION templates cannot have a custom footer"));
                case CAROUSEL, LIMITED_TIME_OFFER ->
                        violations.add(Violation.of(Violation.componentPath(i, "type"),
                                "META_AUTH_COMPONENT_NOT_ALLOWED",
                                c.getType() + " is not allowed on AUTHENTICATION templates"));
                case BUTTONS -> validateAuthButtons(c, i, violations);
            }
        }
    }

    private void validateAuthButtons(WhatsappTemplateComponentRequestDto c, int i,
                                     List<Violation> violations) {

        List<WhatsappTemplateButtonRequestDto> buttons = c.getButtons();
        if (buttons == null) return;

        for (int j = 0; j < buttons.size(); j++) {
            WhatsappTemplateButtonRequestDto b = buttons.get(j);
            if (b == null || b.getType() == null) continue;

            String field = Violation.componentPath(i, "buttons[" + j + "]");

            if (!OTP_BUTTONS.contains(b.getType())) {
                violations.add(Violation.of(field + ".type",
                        "META_AUTH_BUTTON_TYPE_NOT_ALLOWED",
                        "AUTHENTICATION templates allow only OTP buttons, found " + b.getType()));
                continue;
            }

            boolean needsApp = b.getOtpType() == OtpType.ONE_TAP || b.getOtpType() == OtpType.ZERO_TAP;
            if (needsApp && (b.getSupportedApps() == null || b.getSupportedApps().isEmpty())) {
                violations.add(Violation.of(field + ".supportedApps",
                        "META_AUTH_SUPPORTED_APPS_REQUIRED",
                        b.getOtpType() + " OTP buttons require at least one supported app "
                                + "with packageName and signatureHash"));
            }
        }
    }

    // ── MARKETING ──

    private void validateMarketing(List<WhatsappTemplateComponentRequestDto> components,
                                   List<Violation> violations) {

        forEachButton(components, (i, j, b) -> {
            if (OTP_BUTTONS.contains(b.getType())) {
                violations.add(Violation.of(
                        Violation.componentPath(i, "buttons[" + j + "].type"),
                        "META_OTP_BUTTON_AUTH_ONLY",
                        b.getType() + " buttons are only permitted on AUTHENTICATION templates"));
            }
        });

        for (int i = 0; i < components.size(); i++) {
            WhatsappTemplateComponentRequestDto c = components.get(i);
            if (c != null && Boolean.TRUE.equals(c.getAddSecurityRecommendation())) {
                violations.add(Violation.of(
                        Violation.componentPath(i, "addSecurityRecommendation"),
                        "META_SECURITY_RECOMMENDATION_AUTH_ONLY",
                        "addSecurityRecommendation applies only to AUTHENTICATION templates"));
            }
        }
    }

    // ── UTILITY ──

    private void validateUtility(List<WhatsappTemplateComponentRequestDto> components,
                                 List<Violation> violations) {

        forEachButton(components, (i, j, b) -> {
            if (OTP_BUTTONS.contains(b.getType()) || COMMERCE_BUTTONS.contains(b.getType())) {
                violations.add(Violation.of(
                        Violation.componentPath(i, "buttons[" + j + "].type"),
                        "META_BUTTON_TYPE_NOT_ALLOWED_FOR_UTILITY",
                        b.getType() + " buttons are not permitted on UTILITY templates"));
            }
        });

        for (int i = 0; i < components.size(); i++) {
            WhatsappTemplateComponentRequestDto c = components.get(i);
            if (c != null && c.getType() == ComponentType.LIMITED_TIME_OFFER) {
                violations.add(Violation.of(Violation.componentPath(i, "type"),
                        "META_LTO_MARKETING_ONLY",
                        "LIMITED_TIME_OFFER is only permitted on MARKETING templates"));
            }
        }
    }

    // ── helper ──

    @FunctionalInterface
    private interface ButtonVisitor {
        void visit(int componentIndex, int buttonIndex, WhatsappTemplateButtonRequestDto button);
    }

    private void forEachButton(List<WhatsappTemplateComponentRequestDto> components,
                               ButtonVisitor visitor) {
        for (int i = 0; i < components.size(); i++) {
            WhatsappTemplateComponentRequestDto c = components.get(i);
            if (c == null || c.getType() != ComponentType.BUTTONS || c.getButtons() == null) continue;

            List<WhatsappTemplateButtonRequestDto> buttons = c.getButtons();
            for (int j = 0; j < buttons.size(); j++) {
                WhatsappTemplateButtonRequestDto b = buttons.get(j);
                if (b != null && b.getType() != null) {
                    visitor.visit(i, j, b);
                }
            }
        }
    }
}