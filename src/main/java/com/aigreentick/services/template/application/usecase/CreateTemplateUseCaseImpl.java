package com.aigreentick.services.template.application.usecase;

import com.aigreentick.services.template.application.port.in.CreateTemplateUseCase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.api.request.BaseTemplateRequestDto;
import com.aigreentick.services.template.application.dto.command.CreateTemplateCommand;
import com.aigreentick.services.template.application.dto.result.TemplateResult;
import com.aigreentick.services.template.application.mapper.WhatsappTemplateMapper;
import com.aigreentick.services.template.application.service.MetaTemplateSubmissionService;
import com.aigreentick.services.template.application.validation.TemplateValidationService;
import com.aigreentick.services.template.common.util.helper.JsonHelper;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.service.TemplateCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreateTemplateUseCaseImpl implements CreateTemplateUseCase {

    private final TemplateCommandService commandService;
    private final WhatsappTemplateMapper templateMapper;
    private final MetaTemplateSubmissionService metaSubmission;
    private final TemplateValidationService templateValidationService;

    /**
     * Creates a WhatsApp template.
     *
     * Flow:
     *   1. Validate no duplicate exists (ignores DRAFTs)
     *   2. Build and save as DRAFT
     *   3. If isDraft=true → return immediately
     *   4. If isDraft=false → submit to Meta
     */
    @Transactional
    public TemplateResult execute(CreateTemplateCommand command) {

        BaseTemplateRequestDto templateReq = command.getTemplateData();

        // Step 1: Duplicate check

        templateValidationService.validate(templateReq);
        
        commandService.ensureNoDuplicate(
                command.getWabaId(), templateReq.getName(), templateReq.getLanguage(), null);

        // Step 2: Build and save as DRAFT
        String payload = JsonHelper.serializeWithSnakeCase(templateReq);
        WhatsappTemplate template = templateMapper.mapToTemplateEntity(
                payload, command.getProjectId(), command.getOrganizationId(), command);
        template = commandService.save(template);

        log.info("Template saved as DRAFT id={} project={} components={} variables={}",
                template.getId(), command.getProjectId(),
                template.getComponents() != null ? template.getComponents().size() : 0,
                template.getVariables() != null ? template.getVariables().size() : 0);

        // Step 3: Draft-only? Return early
        if (command.isDraft()) {
            return templateMapper.mapToTemplateResponse(template);
        }

        // Step 4: Submit to Meta
        return metaSubmission.submitToMeta(template, payload, command.getWabaId() );
    }
}
