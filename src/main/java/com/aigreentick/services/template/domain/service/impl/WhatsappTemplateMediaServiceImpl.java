package com.aigreentick.services.template.domain.service.impl;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.domain.model.WhatsappTemplateMediaUpload;
import com.aigreentick.services.template.domain.repository.WhatsappTemplateMediaUploadRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsappTemplateMediaServiceImpl {
    private final WhatsappTemplateMediaUploadRepository resumableMediaRepository;

    public WhatsappTemplateMediaUpload save(WhatsappTemplateMediaUpload media) {
        return resumableMediaRepository.save(media);
    }
}
