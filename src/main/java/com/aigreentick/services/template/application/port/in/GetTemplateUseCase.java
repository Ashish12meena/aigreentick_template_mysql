package com.aigreentick.services.template.application.port.in;

import org.springframework.data.domain.Page;

import com.aigreentick.services.template.application.dto.result.TemplateSummaryResult;
import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;

/**
 * Driving port: read-only access to templates — single lookups and a
 * filtered, paginated listing.
 *
 * Implemented by {@link com.aigreentick.services.template.application.usecase.GetTemplateUseCaseImpl}.
 */
public interface GetTemplateUseCase {

    WhatsappTemplate getById(Long templateId, Long projectId);

    WhatsappTemplate getByNameAndLanguage(Long projectId, String name, String language, String wabaId);

    Page<TemplateSummaryResult> list(
            Long projectId, TemplateStatus status, TemplateCategory category,
            String search, int page, int size, String sortBy, String sortDir);
}
