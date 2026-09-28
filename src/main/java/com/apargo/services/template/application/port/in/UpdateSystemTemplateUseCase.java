package com.apargo.services.template.application.port.in;

import com.apargo.services.template.application.dto.command.SaveSystemTemplateCommand;
import com.apargo.services.template.application.dto.result.SystemTemplateDetailResult;

/**
 * Driving port: replace a Template Library entry (content, description and
 * active flag). Templates users already copied are unaffected.
 *
 * Implemented by {@link com.apargo.services.template.application.usecase.UpdateSystemTemplateUseCaseImpl}.
 */
public interface UpdateSystemTemplateUseCase {

    SystemTemplateDetailResult execute(Long systemTemplateId, SaveSystemTemplateCommand command);
}
