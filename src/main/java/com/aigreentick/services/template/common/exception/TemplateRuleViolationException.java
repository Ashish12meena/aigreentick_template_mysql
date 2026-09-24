package com.aigreentick.services.template.common.exception;

import java.util.List;

import com.aigreentick.services.template.application.validation.Violation;
import com.aigreentick.services.template.common.error.ErrorCode;

import lombok.Getter;

/**
 * The payload is well-formed and every field is individually valid, but the
 * combination breaks a Meta composition rule. Rendered as HTTP 422
 * {@code VALIDATION_FAILED} with one {@code errors[]} entry per violation,
 * each keeping its {@code META_*} code.
 */
@Getter
public class TemplateRuleViolationException extends BaseApplicationException {

    private final transient List<Violation> violations;

    public TemplateRuleViolationException(List<Violation> violations) {
        super(ErrorCode.VALIDATION_FAILED,
                "Template violates " + violations.size() + " WhatsApp business rule(s)");
        this.violations = List.copyOf(violations);
    }
}
