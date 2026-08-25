package com.aigreentick.services.template.application.validation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.api.request.BaseTemplateRequestDto;
import com.aigreentick.services.template.common.exception.TemplateRuleViolationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enforces Meta's WhatsApp template business rules before a template is
 * persisted or submitted.
 *
 * Two deliberate behaviours:
 *
 * 1. ALL violations are collected and reported together. Failing on the first
 *    would force a caller into one round trip per mistake.
 *
 * 2. Validators run in dependency order — identity and structure first, since
 *    the later checks read components the structural rules validate. Every
 *    validator is independently null-safe, so a structurally broken template
 *    still produces a complete report rather than an NPE.
 *
 * Scope: only rules that are deterministic and stable. Subjective judgements
 * (content policy, spam signals, URL reputation, category reclassification) are
 * left to Meta and surfaced through its rejection reason.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateValidationService {

    private final TemplateIdentityValidator identityValidator;
    private final TemplateStructureValidator structureValidator;
    private final PlaceholderValidator placeholderValidator;
    private final ButtonValidator buttonValidator;
    private final CategoryValidator categoryValidator;

    public void validate(BaseTemplateRequestDto template) {
        List<Violation> violations = new ArrayList<>();

        identityValidator.validate(template, violations);
        structureValidator.validate(template, violations);
        placeholderValidator.validate(template, violations);
        buttonValidator.validate(template, violations);
        categoryValidator.validate(template, violations);

        if (!violations.isEmpty()) {
            log.info("Template '{}' rejected by {} rule violation(s): {}",
                    template.getName(), violations.size(),
                    violations.stream().map(Violation::code).toList());
            throw new TemplateRuleViolationException(violations);
        }
    }
}