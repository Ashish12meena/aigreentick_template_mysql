package com.apargo.services.template.application.port.in;

import com.apargo.services.template.api.response.TemplateSyncStats;

/**
 * Driving port: pull the latest template state from Meta for a given
 * project/WABA and reconcile it with local records.
 *
 * Implemented by {@link com.apargo.services.template.application.usecase.SyncTemplateFromFacebookUseCaseImpl}.
 */
public interface SyncTemplateFromFacebookUseCase {

    TemplateSyncStats execute(Long projectId, Long organizationId, String wabaId);
}
