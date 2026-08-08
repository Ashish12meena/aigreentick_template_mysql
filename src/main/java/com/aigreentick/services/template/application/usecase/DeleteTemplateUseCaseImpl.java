package com.aigreentick.services.template.application.usecase;

import com.aigreentick.services.template.application.dto.TenantScope;
import com.aigreentick.services.template.application.port.in.DeleteTemplateUseCase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.api.dto.response.client.AccessTokenIdentifier;
import com.aigreentick.services.template.api.dto.response.client.FacebookApiResponse;
import com.aigreentick.services.template.application.port.out.FacebookTemplatePort;
import com.aigreentick.services.template.application.port.out.WabaCredentialPort;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.service.TemplateCommandService;
import com.aigreentick.services.template.domain.service.TemplateQueryService;
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

    /**
     * Soft-deletes a single template by ID.
     * Optionally deletes from Meta first if the template was submitted.
     *
     * @param templateId     the template ID
     * @param projectId      the project scope
     * @param deleteFromMeta if true, also delete from Facebook
     * @return number of deleted records (1 on success)
     */
    @Transactional
    public int deleteById(Long templateId, Long projectId, boolean deleteFromMeta) {
        log.info("Deleting template id={} projectId={} deleteFromMeta={}", templateId, projectId, deleteFromMeta);

        if (deleteFromMeta) {
            WhatsappTemplate template = queryService.getByIdAndProject(templateId, projectId);
            deleteFromFacebookIfApplicable(template);
        }

        int deleted = commandService.softDeleteById(templateId, projectId);
        log.info("Template id={} soft-deleted successfully", templateId);
        return deleted;
    }

    /**
     * Bulk soft-deletes ALL templates for a project.
     * Does NOT delete from Meta — use with caution.
     */
    @Transactional
    public int deleteAllByProject(Long projectId) {
        log.info("Bulk deleting all templates for projectId={}", projectId);

        long count = queryService.countActiveByProject(projectId);
        if (count == 0) {
            log.info("No active templates found for projectId={}", projectId);
            return 0;
        }

        int deleted = commandService.softDeleteAllByProject(projectId);
        log.info("Bulk soft-deleted {} templates for projectId={}", deleted, projectId);
        return deleted;
    }

    /**
     * Attempts to delete template from Facebook if it has a metaTemplateId.
     * Failures are logged but don't block local deletion.
     */
    private void deleteFromFacebookIfApplicable(WhatsappTemplate template) {
        String metaTemplateId = template.getMetaTemplateId();
        if (metaTemplateId == null || metaTemplateId.isBlank()) {
            log.info("Template id={} has no metaTemplateId, skipping Meta deletion", template.getId());
            return;
        }

        try {
            AccessTokenIdentifier credentials = accountClient
                    .getWhatsappAccountWabaAccessToken(template.getWabaId(), TenantScope.of(template));

            if (credentials == null || credentials.getAccessToken().isBlank()) {
                log.warn("No access token found for wabaId={}, skipping Meta deletion",
                        template.getWabaId());
                return;
            }

            FacebookApiResponse<JsonNode> response = ftp.deleteTemplate(
                    template.getName(),
                    template.getWabaId(),
                    credentials.getAccessToken());

            if (response.isSuccess()) {
                log.info("Template deleted from Meta: name={} metaId={}",
                        template.getName(), metaTemplateId);
            } else {
                log.warn("Failed to delete template from Meta: name={} error={}",
                        template.getName(), response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Error deleting template from Meta: name={} metaId={}",
                    template.getName(), metaTemplateId, e);
        }
    }
}