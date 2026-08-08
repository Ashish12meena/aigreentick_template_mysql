package com.aigreentick.services.template.application.port.out;


import java.io.File;
import java.io.IOException;

import com.aigreentick.services.template.api.dto.response.client.FacebookApiResponse;
import com.aigreentick.services.template.api.dto.response.media.UploadMediaResponse;
import com.aigreentick.services.template.api.dto.response.media.UploadOffsetResponse;
import com.aigreentick.services.template.api.dto.response.media.UploadSessionResponse;

public interface FacebookMediaUploadPort {

    FacebookApiResponse<UploadSessionResponse> initiateUploadSession(
            String fileName, long fileSize, String mimeType, String wabaAppId, String accessToken);

    FacebookApiResponse<UploadMediaResponse> uploadResumableMediaToFacebook(
            String sessionId, File file, String accessToken, String offset) throws IOException;

    UploadOffsetResponse getUploadOffset(String sessionId, String accessToken);
}