package com.apargo.services.template.application.port.in;

import com.apargo.services.template.application.dto.command.EditTemplateCommand;
import com.apargo.services.template.application.dto.result.TemplateResult;

/**
 * Driving port: edit a template that exists on Meta (APPROVED, REJECTED or
 * PAUSED) and send it back for review.
 *
 * Implemented by {@link com.apargo.services.template.application.usecase.EditTemplateUseCaseImpl}.
 */
public interface EditTemplateUseCase {

    TemplateResult execute(EditTemplateCommand command);
}
