package com.aigreentick.services.template.application.port.in;

/**
 * Driving port: delete templates, either a single one or every template
 * belonging to a project.
 *
 * Implemented by {@link com.aigreentick.services.template.application.usecase.DeleteTemplateUseCaseImpl}.
 */
public interface DeleteTemplateUseCase {

    int deleteById(Long templateId, Long projectId, boolean deleteFromMeta);

    int deleteAllByProject(Long projectId);
}
