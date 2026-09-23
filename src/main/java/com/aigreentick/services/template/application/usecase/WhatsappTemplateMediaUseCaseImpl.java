package com.aigreentick.services.template.application.usecase;

import com.aigreentick.services.template.application.dto.TenantScope;
import com.aigreentick.services.template.application.port.in.WhatsappTemplateMediaUseCase;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aigreentick.services.template.application.dto.client.AccessTokenIdentifier;
import com.aigreentick.services.template.application.dto.client.FacebookApiResponse;
import com.aigreentick.services.template.api.response.media.ResumableMediaUploadResponseDto;
import com.aigreentick.services.template.api.response.media.UploadMediaResponse;
import com.aigreentick.services.template.api.response.media.UploadSessionResponse;
import com.aigreentick.services.template.application.port.out.FacebookMediaUploadPort;
import com.aigreentick.services.template.application.port.out.WabaCredentialPort;
import com.aigreentick.services.template.common.exception.ExternalServiceException;
import com.aigreentick.services.template.common.exception.MediaUploadException;
import com.aigreentick.services.template.common.util.helper.FileMetaData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsappTemplateMediaUseCaseImpl implements WhatsappTemplateMediaUseCase {
    private final WabaCredentialPort wClient;
    private final FacebookMediaUploadPort fmup;

    public ResumableMediaUploadResponseDto uploadMedia(
            MultipartFile file, Long projectId, Long organizationId,
            String wabaId, String appId) {

        AccessTokenIdentifier accessTokenIdentifier = wClient.getWhatsappAccountWabaAccessToken(wabaId,new TenantScope(organizationId, projectId));
        String offset = "0";
        File fullFile = null;

        try {
            FileMetaData fileMeta = extractFileDetails(file);
            fullFile = convertMultipartToFile(file);

            FacebookApiResponse<UploadSessionResponse> sessionResponse = fmup.initiateUploadSession(
                    fileMeta.getFileName(), fileMeta.getFileSize(), fileMeta.getMimeType(),
                    appId, accessTokenIdentifier.getAccessToken());

            if (!sessionResponse.isSuccess()) {
                throw new ExternalServiceException(sessionResponse.getErrorMessage());
            }

            String sessionId = sessionResponse.getData().getUploadSessionId();

            FacebookApiResponse<UploadMediaResponse> uploadMediaResponse = tryUploadToFacebook(
                    sessionId, fullFile, accessTokenIdentifier.getAccessToken(), offset);

            if (!uploadMediaResponse.isSuccess()) {
                throw new ExternalServiceException(sessionResponse.getErrorMessage());
            }

            return ResumableMediaUploadResponseDto.builder()
            .fileName(fileMeta.getFileName())
            .fileSize(fileMeta.getFileSize())
            .mimeType(fileMeta.getMimeType())
            .mediaUrl(uploadMediaResponse.getData().getFacebookImageUrl())
            .sessionId(sessionId)
            .build();

        } catch (IOException ex) {
            throw new MediaUploadException("Failed to upload file", ex);
        } finally {
            // Always clean up the temp file to prevent disk fill-up
            if (fullFile != null && fullFile.exists()) {
                if (!fullFile.delete()) {
                    log.warn("Failed to delete temp file: {}", fullFile.getAbsolutePath());
                }
            }
        }
    }

    private FacebookApiResponse<UploadMediaResponse> tryUploadToFacebook(
            String sessionId, File file, String accessToken, String offset) {
        try {
            return fmup.uploadResumableMediaToFacebook(sessionId, file, accessToken, offset);
        } catch (Exception e) {
            log.warn("Initial upload failed, attempting to resume with offset...", e);
            try {
                String newOffset = fmup
                        .getUploadOffset(sessionId, accessToken)
                        .getFileOffset();

                return fmup.uploadResumableMediaToFacebook(sessionId, file, accessToken, newOffset);
            } catch (IOException retryEx) {
                throw new MediaUploadException("Retry upload failed", retryEx);
            }
        }
    }

    private FileMetaData extractFileDetails(MultipartFile file) {
        return new FileMetaData(
                Objects.requireNonNull(file.getOriginalFilename()),
                file.getSize(),
                file.getContentType());
    }

    private File convertMultipartToFile(MultipartFile file) throws IOException {
        File convFile = File.createTempFile("upload_", Objects.requireNonNull(file.getOriginalFilename()));
        file.transferTo(convFile);
        return convFile;
    }
}