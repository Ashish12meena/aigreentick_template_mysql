package com.aigreentick.services.template.application.port.in;

import org.springframework.data.domain.Page;

import com.aigreentick.services.template.application.dto.result.TemplateDetailResult;
import com.aigreentick.services.template.application.dto.result.TemplateSummaryResult;
import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;

/**
 * Driving port: read-only access to templates — single lookups and a
 * filtered, paginated listing.
 *
 * <p>Single-template lookups return {@link TemplateDetailResult}, not the
 * {@code WhatsappTemplate} entity: the implementation walks the full
 * component/button/carousel graph while its transaction is open and hands
 * back a flat, detached result, so callers (controllers) never touch a
 * lazy-loaded entity outside a session.
 *
 * Implemented by {@link com.aigreentick.services.template.application.usecase.GetTemplateUseCaseImpl}.
 */
public interface GetTemplateUseCase {

    TemplateDetailResult getById(Long templateId, Long projectId);

    TemplateDetailResult getByNameAndLanguage(Long projectId, String name, String language, String wabaId);

    Page<TemplateSummaryResult> list(
            Long projectId, TemplateStatus status, TemplateCategory category,
            String search, int page, int size, String sortBy, String sortDir);
}
