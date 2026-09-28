package com.apargo.services.template.application.port.in;

import com.apargo.services.template.application.dto.result.TemplateResult;

/**
 * Driving port: submit a DRAFT template to Meta for review.
 *
 * Implemented by {@link com.apargo.services.template.application.usecase.SubmitDraftToMetaUseCaseImpl}.
 */
public interface SubmitDraftToMetaUseCase {

    TemplateResult execute(Long templateId, Long projectId);
}
