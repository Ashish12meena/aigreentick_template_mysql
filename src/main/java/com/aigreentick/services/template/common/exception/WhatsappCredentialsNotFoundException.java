package com.aigreentick.services.template.common.exception;

import com.aigreentick.services.template.common.error.ErrorCode;

/**
 * WABA credentials (Meta access token) could not be resolved from
 * waba-service. An upstream dependency failure, not a client error: HTTP 502
 * {@code WABA_CREDENTIALS_UNAVAILABLE}.
 */
public class WhatsappCredentialsNotFoundException extends BaseApplicationException {

    public WhatsappCredentialsNotFoundException(String message) {
        super(ErrorCode.WABA_CREDENTIALS_UNAVAILABLE, message);
    }
}
