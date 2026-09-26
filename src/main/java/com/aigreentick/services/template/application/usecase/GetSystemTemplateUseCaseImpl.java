package com.aigreentick.services.template.application.usecase;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.application.dto.result.SystemTemplateDetailResult;
import com.aigreentick.services.template.application.dto.result.SystemTemplateSummaryResult;
import com.aigreentick.services.template.application.mapper.SystemTemplateMapper;
import com.aigreentick.services.template.application.port.in.GetSystemTemplateUseCase;
import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.service.TemplateQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetSystemTemplateUseCaseImpl implements GetSystemTemplateUseCase {

    private final TemplateQueryService queryService;
    private final SystemTemplateMapper systemTemplateMapper;

    @Override
    public SystemTemplateDetailResult getById(Long systemTemplateId) {
        log.info("Fetching library template id={}", systemTemplateId);
        return systemTemplateMapper.toDetail(queryService.getActiveSystemTemplate(systemTemplateId));
    }

    @Override
    public Page<SystemTemplateSummaryResult> list(
            TemplateCategory category, String language, String search,
            int page, int size, String sortBy, String sortDir) {

        log.info("Listing library templates category={} language={}", category, language);
        return systemTemplateMapper.toPage(queryService.listActiveSystemTemplates(
                category, language, search, page, size, sortBy, sortDir));
    }
}
