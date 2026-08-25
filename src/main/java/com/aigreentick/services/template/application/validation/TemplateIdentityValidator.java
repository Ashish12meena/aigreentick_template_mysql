package com.aigreentick.services.template.application.validation;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.request.BaseTemplateRequestDto;
import com.aigreentick.services.template.application.validation.support.SupportedLanguages;

/** Template name and language — the two identity fields Meta validates strictly. */
@Component
public class TemplateIdentityValidator {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-z0-9_]+$");
    private static final int NAME_MAX = 512;

    public void validate(BaseTemplateRequestDto template, List<Violation> violations) {
        validateName(template.getName(), violations);
        validateLanguage(template.getLanguage(), violations);
    }

    private void validateName(String name, List<Violation> violations) {
        if (name == null) return;   // @NotBlank already reported it

        if (!VALID_NAME.matcher(name).matches()) {
            violations.add(Violation.of("template.name", "META_NAME_INVALID_CHARS",
                    "Template name may contain only lowercase letters, digits and underscores"));
        }
        if (name.length() > NAME_MAX) {
            violations.add(Violation.of("template.name", "META_NAME_TOO_LONG",
                    "Template name must not exceed " + NAME_MAX + " characters"));
        }
    }

    private void validateLanguage(String language, List<Violation> violations) {
        if (language == null) return;

        if (!SupportedLanguages.isSupported(language)) {
            violations.add(Violation.of("template.language", "META_LANGUAGE_UNSUPPORTED",
                    "'" + language + "' is not a Meta-supported template language code"));
        }
    }
}