package com.aigreentick.services.template.application.port.in;

import com.aigreentick.services.template.application.dto.command.SaveSystemTemplateCommand;
import com.aigreentick.services.template.application.dto.result.SystemTemplateDetailResult;

/**
 * Driving port: add a template to the Template Library. The template is
 * validated against the same Meta rules as a user's template.
 *
 * Implemented by {@link com.aigreentick.services.template.application.usecase.CreateSystemTemplateUseCaseImpl}.
 */
public interface CreateSystemTemplateUseCase {

    SystemTemplateDetailResult execute(SaveSystemTemplateCommand command);
}
