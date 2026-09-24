package com.aigreentick.services.template.common.exception;

import com.aigreentick.services.template.common.error.ErrorCode;

/**
 * The template's current status does not allow the operation, e.g. updating
 * or submitting a template that is no longer a draft. The request itself is
 * valid; it clashes with the current state, hence HTTP 409
 * {@code TEMPLATE_INVALID_STATE} (API Standard §6).
 */
public class InvalidTemplateStateException extends BaseApplicationException {

    public InvalidTemplateStateException(String message) {
        super(ErrorCode.TEMPLATE_INVALID_STATE, message);
    }
}
