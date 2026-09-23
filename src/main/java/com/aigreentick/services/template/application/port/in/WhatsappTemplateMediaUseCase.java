package com.aigreentick.services.template.application.port.in;

import org.springframework.web.multipart.MultipartFile;

import com.aigreentick.services.template.api.response.media.ResumableMediaUploadResponseDto;

/**
 * Driving port: upload header media (image/video/document) to Meta ahead
 * of attaching it to a template component.
 *
 * Implemented by {@link com.aigreentick.services.template.application.usecase.WhatsappTemplateMediaUseCaseImpl}.
 */
public interface WhatsappTemplateMediaUseCase {

    /**
     * @param wabaId WABA whose access token (from waba-service) authorises the upload
     * @param appId  Meta app id the upload session is opened on ({@code /{appId}/uploads})
     */
    ResumableMediaUploadResponseDto uploadMedia(
            MultipartFile file, Long projectId, Long organizationId, String wabaId, String appId);
}
