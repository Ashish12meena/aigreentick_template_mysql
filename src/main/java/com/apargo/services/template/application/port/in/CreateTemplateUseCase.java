package com.apargo.services.template.application.port.in;

import com.apargo.services.template.application.dto.command.CreateTemplateCommand;
import com.apargo.services.template.application.dto.result.TemplateResult;

/**
 * Driving port: create a new WhatsApp template (as DRAFT or submitted
 * directly, depending on {@code command.isDraft()}).
 *
 * Implemented by {@link com.apargo.services.template.application.usecase.CreateTemplateUseCaseImpl}.
 *
 * Note: this interface only depends on {@code application.dto} — never on
 * {@code api.dto}. {@code api.mapper} is responsible for converting the
 * REST request into {@link CreateTemplateCommand} and the returned
 * {@link TemplateResult} back into an HTTP response body.
 */
public interface CreateTemplateUseCase {

    TemplateResult execute(CreateTemplateCommand command);
}
