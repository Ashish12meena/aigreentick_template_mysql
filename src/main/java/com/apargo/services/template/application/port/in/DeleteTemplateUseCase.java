package com.apargo.services.template.application.port.in;

/**
 * Driving port: delete templates, either a single one or every template
 * belonging to a project.
 *
 * Implemented by {@link com.apargo.services.template.application.usecase.DeleteTemplateUseCaseImpl}.
 */
public interface DeleteTemplateUseCase {

    int deleteById(Long templateId, Long projectId, boolean deleteFromMeta);

    /**
     * @param organizationId the caller's organization; not a filter (data is
     *                       scoped by project), recorded on the audit event
     */
    int deleteAllByProject(Long organizationId, Long projectId);
}
