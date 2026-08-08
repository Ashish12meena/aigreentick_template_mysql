package com.aigreentick.services.template.application.port.in;

import com.aigreentick.services.template.application.dto.command.UpdateDraftTemplateCommand;
import com.aigreentick.services.template.application.dto.result.TemplateResult;

/**
 * Driving port: update a template that is still in DRAFT state.
 *
 * Implemented by {@link com.aigreentick.services.template.application.usecase.UpdateDraftTemplateUseCaseImpl}.
 */
public interface UpdateDraftTemplateUseCase {

    TemplateResult execute(UpdateDraftTemplateCommand command);
}
