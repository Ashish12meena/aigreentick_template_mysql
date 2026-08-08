package com.aigreentick.services.template.application.validation;

/**
 * A single Meta business rule violation.
 *
 * @param field   dotted path into the request, e.g. "template.components[0].text"
 * @param code    stable machine-readable identifier — this is what clients branch on.
 *                Never let callers parse the human message.
 * @param message human-readable explanation
 */
public record Violation(String field, String code, String message) {

    public static Violation of(String field, String code, String message) {
        return new Violation(field, code, message);
    }

    /** Path helper so validators build consistent field paths. */
    public static String componentPath(int index, String field) {
        return "template.components[" + index + "]." + field;
    }
}