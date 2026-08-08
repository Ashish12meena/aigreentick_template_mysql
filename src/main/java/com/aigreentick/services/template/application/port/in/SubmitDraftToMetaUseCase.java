package com.aigreentick.services.template.application.port.in;

import com.aigreentick.services.template.application.dto.result.TemplateResult;

/**
 * Driving port: submit a DRAFT template to Meta for review.
 *
 * Implemented by {@link com.aigreentick.services.template.application.usecase.SubmitDraftToMetaUseCaseImpl}.
 */
public interface SubmitDraftToMetaUseCase {

    TemplateResult execute(Long templateId, Long projectId);
}
