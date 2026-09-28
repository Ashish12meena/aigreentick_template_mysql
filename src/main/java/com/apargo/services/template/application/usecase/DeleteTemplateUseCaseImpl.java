package com.apargo.services.template.application.usecase;

import com.apargo.services.template.application.audit.TemplateAuditEvents;
import com.apargo.services.template.application.audit.TemplateAuditEvents.MetaDeletion;
import com.apargo.services.template.application.audit.TemplateAuditSnapshot;
import com.apargo.services.template.application.dto.TenantScope;
import com.apargo.services.template.application.port.in.DeleteTemplateUseCase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.apargo.services.template.application.dto.client.AccessTokenIdentifier;
import com.apargo.services.template.application.dto.client.FacebookApiResponse;
import com.apargo.services.template.application.port.out.FacebookTemplatePort;
import com.apargo.services.template.application.port.out.WabaCredentialPort;
import com.apargo.services.template.domain.model.WhatsappTemplate;
import com.apargo.services.template.domain.service.TemplateCommandService;
import com.apargo.services.template.domain.service.TemplateQueryService;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeleteTemplateUseCaseImpl implements DeleteTemplateUseCase {

    private final TemplateQueryService queryService;
    private final TemplateCommandService commandService;
    private final WabaCredentialPort accountClient;
    private final FacebookTemplatePort  ftp;
    private final TemplateAuditEvents audit;

    /**
     * Soft-deletes a single template by ID.
     * Optionally deletes from Meta first if the template was submitted.
     * The row is read first (404 if absent) so the audit event can name it.
     *
     * @param templateId     the template ID
     * @param projectId      the project scope
     * @param deleteFromMeta if true, also delete from Facebook
     * @return number of deleted records (1 on success)
     */
    @Transactional
    public int deleteById(Long templateId, Long projectId, boolean deleteFromMeta) {
        log.info("Deleting template id={} projectId={} deleteFromMeta={}", templateId, projectId, deleteFromMeta);

        WhatsappTemplate template = queryService.getByIdAndProject(templateId, projectId);
        TemplateAuditSnapshot snapshot = TemplateAuditSnapshot.of(template);

        MetaDeletion metaDeletion = deleteFromMeta
                ? deleteFromFacebookIfApplicable(template)
                : MetaDeletion.NOT_REQUESTED;

        int deleted = commandService.softDeleteById(templateId, projectId);
        log.info("Template id={} soft-deleted successfully", templateId);

        // Published only if this transaction commits.
        audit.templateDeleted(snapshot, metaDeletion);
        return deleted;
    }

    /**
     * Bulk soft-deletes ALL templates for a project.
     * Does NOT delete from Meta — use with caution.
     */
    @Transactional
    public int deleteAllByProject(Long organizationId, Long projectId) {
        log.info("Bulk deleting all templates for projectId={}", projectId);

        long count = queryService.countActiveByProject(projectId);
        if (count == 0) {
            log.info("No active templates found for projectId={}", projectId);
            return 0;
        }

        int deleted = commandService.softDeleteAllByProject(projectId);
        log.info("Bulk soft-deleted {} templates for projectId={}", deleted, projectId);
        if (deleted > 0) {
            audit.templatesBulkDeleted(organizationId, projectId, deleted);
        }
        return deleted;
    }

    /**
     * Attempts to delete template from Facebook if it has a metaTemplateId.
     * Failures are logged but don't block local deletion; the outcome is
     * returned for the audit event.
     */
    private MetaDeletion deleteFromFacebookIfApplicable(WhatsappTemplate template) {
        String metaTemplateId = template.getMetaTemplateId();
        if (metaTemplateId == null || metaTemplateId.isBlank()) {
            log.info("Template id={} has no metaTemplateId, skipping Meta deletion", template.getId());
            return MetaDeletion.NOT_ON_META;
        }

        try {
            AccessTokenIdentifier credentials = accountClient
                    .getWhatsappAccountWabaAccessToken(template.getWabaId(), TenantScope.of(template));

            if (credentials == null || credentials.getAccessToken() == null
                    || credentials.getAccessToken().isBlank()) {
                log.warn("No access token found for wabaId={}, skipping Meta deletion",
                        template.getWabaId());
                return MetaDeletion.FAILED;
            }

            FacebookApiResponse<JsonNode> response = ftp.deleteTemplate(
                    template.getName(),
                    template.getWabaId(),
                    credentials.getAccessToken());

            if (response.isSuccess()) {
                log.info("Template deleted from Meta: name={} metaId={}",
                        template.getName(), metaTemplateId);
                return MetaDeletion.DELETED;
            }
            log.warn("Failed to delete template from Meta: name={} error={}",
                    template.getName(), response.getErrorMessage());
            return MetaDeletion.FAILED;
        } catch (Exception e) {
            log.error("Error deleting template from Meta: name={} metaId={}",
                    template.getName(), metaTemplateId, e);
            return MetaDeletion.FAILED;
        }
    }
}