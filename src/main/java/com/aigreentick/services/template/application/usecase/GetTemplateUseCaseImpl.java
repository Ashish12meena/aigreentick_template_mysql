package com.aigreentick.services.template.application.usecase;

import com.aigreentick.services.template.application.port.in.GetTemplateUseCase;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.application.dto.result.TemplateDetailResult;
import com.aigreentick.services.template.application.dto.result.TemplateSummaryResult;
import com.aigreentick.services.template.application.mapper.TemplateDetailResultMapper;
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
    private final TemplateDetailResultMapper detailResultMapper;

    public TemplateDetailResult getById(Long templateId, Long projectId) {
        log.info("Fetching template id={} projectId={}", templateId, projectId);
        WhatsappTemplate template = queryService.getByIdAndProject(templateId, projectId);
        // Mapped here, inside the still-open transaction, so every lazy
        // collection on the entity graph (components/buttons/carousel/...)
        // is loaded and flattened before it ever leaves this method.
        return detailResultMapper.toDetailResult(template);
    }

    public TemplateDetailResult getByNameAndLanguage(
            Long projectId, String name, String language, String wabaId) {
        log.info("Fetching template name={} language={} projectId={}", name, language, projectId);
        WhatsappTemplate template = queryService.getByNameLanguageAndWaba(projectId, name, language, wabaId);
        return detailResultMapper.toDetailResult(template);
    }

    public Page<TemplateSummaryResult> list(
            Long projectId, TemplateStatus status, TemplateCategory category,
            String search, int page, int size, String sortBy, String sortDir) {

        log.info("Listing templates projectId={} status={} category={}", projectId, status, category);
        return resultMapper.toPage(
                queryService.listByProject(projectId, status, category, search, page, size, sortBy, sortDir));
    }
}
