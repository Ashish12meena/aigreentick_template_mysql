package com.aigreentick.services.template.api.request;

import com.aigreentick.services.template.domain.enums.MediaLocation;
import lombok.Data;

@Data
public class MediaUrlMappingRequestDto {

    // /**
    //  * TEMPLATE_HEADER — main template header media
    //  * CAROUSEL_CARD_HEADER — carousel card header media
    //  */
    private MediaLocation location;

    /**
     * For CAROUSEL_CARD_HEADER: which card (0-based).
     * Ignored for TEMPLATE_HEADER.
     */
    private Integer cardIndex= -1;

    /**
     * Internal Media Service URL
     * e.g. http://localhost:7998/api/v1/media/serve/org-2/proj-3/image/uuid.png
     */
    private String mediaUrl;
}