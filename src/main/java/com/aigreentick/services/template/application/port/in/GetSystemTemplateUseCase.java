package com.aigreentick.services.template.application.port.in;

import org.springframework.data.domain.Page;

import com.aigreentick.services.template.application.dto.result.SystemTemplateDetailResult;
import com.aigreentick.services.template.application.dto.result.SystemTemplateSummaryResult;
import com.aigreentick.services.template.domain.enums.TemplateCategory;

/**
 * Driving port: read the Template Library. Only active entries are visible,
 * and nothing is scoped by project — library templates belong to the system.
 *
 * Implemented by {@link com.aigreentick.services.template.application.usecase.GetSystemTemplateUseCaseImpl}.
 */
public interface GetSystemTemplateUseCase {

    SystemTemplateDetailResult getById(Long systemTemplateId);

    Page<SystemTemplateSummaryResult> list(
            TemplateCategory category, String language, String search,
            int page, int size, String sortBy, String sortDir);
}
