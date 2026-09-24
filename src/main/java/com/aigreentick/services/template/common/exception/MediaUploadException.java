package com.aigreentick.services.template.common.exception;

import com.aigreentick.services.template.common.error.ErrorCode;

/**
 * Media upload to Meta or to storage-service failed. HTTP 502
 * {@code MEDIA_UPLOAD_FAILED}.
 */
public class MediaUploadException extends BaseApplicationException {

    public MediaUploadException(String message) {
        super(ErrorCode.MEDIA_UPLOAD_FAILED, message);
    }

    public MediaUploadException(String message, Throwable cause) {
        super(ErrorCode.MEDIA_UPLOAD_FAILED, message, cause);
    }
}
