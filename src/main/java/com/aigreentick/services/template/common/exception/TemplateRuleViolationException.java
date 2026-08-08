package com.aigreentick.services.template.common.exception;

import java.util.List;

import com.aigreentick.services.template.application.validation.Violation;

import lombok.Getter;

/**
 * The payload is well-formed and every field is individually valid, but the
 * combination breaks a Meta business rule — hence 422, not 400.
 */
@Getter
public class TemplateRuleViolationException extends RuntimeException {

    private final transient List<Violation> violations;

    public TemplateRuleViolationException(List<Violation> violations) {
        super("Template violates " + violations.size() + " WhatsApp business rule(s)");
        this.violations = List.copyOf(violations);
    }
}