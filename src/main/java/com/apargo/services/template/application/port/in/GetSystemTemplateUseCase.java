package com.apargo.services.template.application.port.in;

import org.springframework.data.domain.Page;

import com.apargo.services.template.application.dto.result.SystemTemplateDetailResult;
import com.apargo.services.template.application.dto.result.SystemTemplateSummaryResult;
import com.apargo.services.template.domain.enums.TemplateCategory;

/**
 * Driving port: read the Template Library. Only active entries are visible,
 * and nothing is scoped by project — library templates belong to the system.
 *
 * Implemented by {@link com.apargo.services.template.application.usecase.GetSystemTemplateUseCaseImpl}.
 */
public interface GetSystemTemplateUseCase {

    SystemTemplateDetailResult getById(Long systemTemplateId);

    Page<SystemTemplateSummaryResult> list(
            TemplateCategory category, String language, String search,
            int page, int size, String sortBy, String sortDir);
}
