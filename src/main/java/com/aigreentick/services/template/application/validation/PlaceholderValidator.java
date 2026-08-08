package com.aigreentick.services.template.application.validation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.dto.request.create.*;
import com.aigreentick.services.template.application.validation.support.Placeholders;
import com.aigreentick.services.template.domain.enums.ComponentType;

/**
 * Placeholder sequencing and example values.
 *
 * The example count mismatch is the single most frequent Meta rejection, and
 * the start/end-with-variable rules are close behind.
 */
@Component
public class PlaceholderValidator {

    public void validate(BaseTemplateRequestDto template, List<Violation> violations) {
        List<WhatsappTemplateComponentRequestDto> components = template.getComponents();
        if (components == null) return;

        for (int i = 0; i < components.size(); i++) {
            WhatsappTemplateComponentRequestDto c = components.get(i);
            if (c == null || c.getText() == null || !c.getText().contains("{{")) continue;

            validateSequencing(c, i, violations);
            validateExamples(c, i, violations);
        }
    }

    private void validateSequencing(WhatsappTemplateComponentRequestDto c, int i,
                                    List<Violation> violations) {

        String text = c.getText();
        String field = Violation.componentPath(i, "text");

        if (Placeholders.hasNamed(text) && Placeholders.hasPositional(text)) {
            violations.add(Violation.of(field, "META_PLACEHOLDER_MIXED_TYPES",
                    "Positional {{1}} and named {{name}} parameters cannot be mixed"));
            return;   // index checks are meaningless once mixed
        }

        List<Integer> indexes = Placeholders.positionalIndexes(text);
        if (!indexes.isEmpty()) {
            if (indexes.get(0) != 1) {
                violations.add(Violation.of(field, "META_PLACEHOLDER_MUST_START_AT_ONE",
                        "Placeholders must start at {{1}}"));
            }
            for (int n = 0; n < indexes.size(); n++) {
                if (indexes.get(n) != n + 1) {
                    violations.add(Violation.of(field, "META_PLACEHOLDER_NOT_SEQUENTIAL",
                            "Placeholders must be sequential with no gaps"));
                    break;
                }
            }
        }

        if (c.getType() == ComponentType.HEADER && indexes.size() > 1) {
            violations.add(Violation.of(field, "META_HEADER_ONE_VARIABLE",
                    "A text HEADER may contain at most one variable"));
        }

        if (c.getType() == ComponentType.BODY) {
            if (Placeholders.startsWithPlaceholder(text)) {
                violations.add(Violation.of(field, "META_BODY_STARTS_WITH_VARIABLE",
                        "BODY must not begin with a variable"));
            }
            if (Placeholders.endsWithPlaceholder(text)) {
                violations.add(Violation.of(field, "META_BODY_ENDS_WITH_VARIABLE",
                        "BODY must not end with a variable"));
            }
        }

        if (Placeholders.hasAdjacentPlaceholders(text)) {
            violations.add(Violation.of(field, "META_PLACEHOLDER_ADJACENT",
                    "Two variables must be separated by text"));
        }
    }

    private void validateExamples(WhatsappTemplateComponentRequestDto c, int i,
                                  List<Violation> violations) {

        int count = Placeholders.positionalIndexes(c.getText()).size();
        if (count == 0 || c.getType() == null) return;

        WhatsappTemplateExampleRequestDto example = c.getExample();

        if (c.getType() == ComponentType.BODY) {
            List<List<String>> bodyText = example == null ? null : example.getBodyText();
            String field = Violation.componentPath(i, "example.bodyText");

            if (bodyText == null || bodyText.isEmpty() || bodyText.get(0) == null) {
                violations.add(Violation.of(field, "META_EXAMPLE_REQUIRED",
                        "BODY contains " + count + " variable(s) and therefore requires example values"));
            } else if (bodyText.get(0).size() != count) {
                violations.add(Violation.of(field, "META_EXAMPLE_COUNT_MISMATCH",
                        "BODY has " + count + " variable(s) but "
                                + bodyText.get(0).size() + " example value(s)"));
            }
        }

        if (c.getType() == ComponentType.HEADER) {
            List<String> headerText = example == null ? null : example.getHeaderText();
            String field = Violation.componentPath(i, "example.headerText");

            if (headerText == null || headerText.isEmpty()) {
                violations.add(Violation.of(field, "META_EXAMPLE_REQUIRED",
                        "HEADER contains a variable and therefore requires an example value"));
            } else if (headerText.size() != count) {
                violations.add(Violation.of(field, "META_EXAMPLE_COUNT_MISMATCH",
                        "HEADER has " + count + " variable(s) but "
                                + headerText.size() + " example value(s)"));
            }
        }
    }
}