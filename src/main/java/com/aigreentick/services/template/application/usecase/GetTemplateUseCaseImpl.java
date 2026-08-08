package com.aigreentick.services.template.application.usecase;

import com.aigreentick.services.template.application.port.in.GetTemplateUseCase;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.application.dto.result.TemplateSummaryResult;
import com.aigreentick.services.template.application.mapper.TemplateResultMapper;
import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.service.TemplateQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetTemplateUseCaseImpl implements GetTemplateUseCase {

    private final TemplateQueryService queryService;
    private final TemplateResultMapper resultMapper;

    public WhatsappTemplate getById(Long templateId, Long projectId) {
        log.info("Fetching template id={} projectId={}", templateId, projectId);
        return queryService.getByIdAndProject(templateId, projectId);
    }

    public WhatsappTemplate getByNameAndLanguage(
            Long projectId, String name, String language, String wabaId) {
        log.info("Fetching template name={} language={} projectId={}", name, language, projectId);
        return queryService.getByNameLanguageAndWaba(projectId, name, language, wabaId);
    }

    public Page<TemplateSummaryResult> list(
            Long projectId, TemplateStatus status, TemplateCategory category,
            String search, int page, int size, String sortBy, String sortDir) {

        log.info("Listing templates projectId={} status={} category={}", projectId, status, category);
        return resultMapper.toPage(
                queryService.listByProject(projectId, status, category, search, page, size, sortBy, sortDir));
    }
}
