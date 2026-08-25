package com.aigreentick.services.template.application.usecase;

import com.aigreentick.services.template.application.port.in.UpdateDraftTemplateUseCase;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.api.request.BaseTemplateRequestDto;
import com.aigreentick.services.template.application.dto.command.UpdateDraftTemplateCommand;
import com.aigreentick.services.template.application.dto.result.TemplateResult;
import com.aigreentick.services.template.application.mapper.WhatsappTemplateMapper;
import com.aigreentick.services.template.common.util.helper.JsonHelper;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.model.WhatsappTemplateComponent;
import com.aigreentick.services.template.domain.model.WhatsappTemplateVariable;
import com.aigreentick.services.template.domain.service.TemplateCommandService;
import com.aigreentick.services.template.domain.service.TemplateQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class UpdateDraftTemplateUseCaseImpl implements UpdateDraftTemplateUseCase {

    private final TemplateQueryService queryService;
    private final TemplateCommandService commandService;
    private final WhatsappTemplateMapper templateMapper;

    /**
     * Updates an existing DRAFT template's components, variables, and metadata.
     * Only DRAFT templates can be updated — submitted/approved ones are immutable.
     *
     * Flow:
     *   1. Verify template exists and is in DRAFT status
     *   2. Update basic metadata fields
     *   3. Re-serialize submission payload
     *   4. Clear and replace components (orphanRemoval handles DB cleanup)
     *   5. Clear and replace variables
     *   6. Save updated entity
     */
    @Transactional
    public TemplateResult execute(UpdateDraftTemplateCommand command) {

        log.info("Updating draft template id={} projectId={}", command.getTemplateId(), command.getProjectId());

        // Step 1: Fetch and validate it's a DRAFT
        WhatsappTemplate existing = queryService.getDraftByIdAndProject(
                command.getTemplateId(), command.getProjectId());

        BaseTemplateRequestDto templateReq = command.getTemplateData();

        // Step 2: Update basic fields

        commandService.ensureNoDuplicate(
                command.getWabaId(), templateReq.getName(), templateReq.getLanguage(),
                command.getTemplateId());

        existing.setName(templateReq.getName());
        existing.setCategory(templateReq.getCategory());
        existing.setLanguage(templateReq.getLanguage());
        existing.setWabaId(command.getWabaId());

        // Step 3: Re-serialize submission payload
        String payload = JsonHelper.serializeWithSnakeCase(templateReq);
        existing.setSubmissionPayload(payload);

        // Step 4: Clear and replace components (orphanRemoval deletes old ones)
        existing.getComponents().clear();
        if (templateReq.getComponents() != null && !templateReq.getComponents().isEmpty()) {
            List<WhatsappTemplateComponent> newComponents = templateMapper
                    .mapComponents(templateReq.getComponents(), existing);
            newComponents.forEach(existing::addComponent);
        }

        // Step 5: Clear and replace variables
        existing.getVariables().clear();
        if (command.getVariables() != null && !command.getVariables().isEmpty()) {
            List<WhatsappTemplateVariable> newVariables = templateMapper
                    .mapVariables(command.getVariables(), existing);
            newVariables.forEach(existing::addVariable);
        }

        // Step 6: Save
        WhatsappTemplate saved = commandService.save(existing);

        int componentCount = saved.getComponents() != null ? saved.getComponents().size() : 0;
        int variableCount = saved.getVariables() != null ? saved.getVariables().size() : 0;
        log.info("Draft template updated id={} components={} variables={}",
                saved.getId(), componentCount, variableCount);

        return templateMapper.mapToTemplateResponse(saved);
    }
}
