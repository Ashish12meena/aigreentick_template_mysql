package com.apargo.services.template.application.port.in;

import com.apargo.services.template.application.dto.command.UpdateDraftTemplateCommand;
import com.apargo.services.template.application.dto.result.TemplateResult;

/**
 * Driving port: update a template that is still in DRAFT state.
 *
 * Implemented by {@link com.apargo.services.template.application.usecase.UpdateDraftTemplateUseCaseImpl}.
 */
public interface UpdateDraftTemplateUseCase {

    TemplateResult execute(UpdateDraftTemplateCommand command);
}
