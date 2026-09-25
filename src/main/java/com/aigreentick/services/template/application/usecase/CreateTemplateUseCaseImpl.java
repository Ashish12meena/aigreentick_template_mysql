package com.aigreentick.services.template.application.usecase;

import com.aigreentick.services.template.application.port.in.CreateTemplateUseCase;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.api.request.BaseTemplateRequestDto;
import com.aigreentick.services.template.application.dto.command.CreateTemplateCommand;
import com.aigreentick.services.template.application.dto.result.TemplateResult;
import com.aigreentick.services.template.application.mapper.WhatsappTemplateMapper;
import com.aigreentick.services.template.application.service.MetaTemplateSubmissionService;
import com.aigreentick.services.template.application.validation.TemplateValidationService;
import com.aigreentick.services.template.common.util.helper.JsonHelper;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
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
     *   1. Validate and check no LIVE duplicate exists (DRAFT / FAILED rows
     *      with the same name do not count - they are not on Meta)
     *   2. Save: DRAFT if isDraft, otherwise SUBMITTED - committed immediately
     *   3. If isDraft=true -> return
     *   4. If isDraft=false -> submit to Meta (no transaction held meanwhile)
     *
     * <h2>Why this method is not @Transactional</h2>
     * Step 2 commits on its own (TemplateCommandService.save) before Meta is
     * called. Saving straight as SUBMITTED makes the row live, so
     * uk_waba_template_live rejects a concurrent create of the same name here -
     * before either request reaches Meta. After this point the row always
     * exists: a crash can at worst leave it SUBMITTED, which the reconciler
     * settles. It can no longer be rolled back out from under a template Meta
     * has already created.
     */
    public TemplateResult execute(CreateTemplateCommand command) {

        BaseTemplateRequestDto templateReq = command.getTemplateData();

        // Step 1: Validation and duplicate check (clean 409 before the insert;
        // the unique key is the real guarantee under concurrency)
        templateValidationService.validate(templateReq);

        commandService.ensureNoDuplicate(
                command.getWabaId(), templateReq.getName(), templateReq.getLanguage(), null);

        // Step 2: Build and save - committed when save() returns
        String payload = JsonHelper.serializeWithSnakeCase(templateReq);
        WhatsappTemplate template = templateMapper.mapToTemplateEntity(
                payload, command.getProjectId(), command.getOrganizationId(), command);
        template.setStatus(command.isDraft() ? TemplateStatus.DRAFT : TemplateStatus.SUBMITTED);
        template = commandService.save(template);

        log.info("Template saved as {} id={} project={} components={} variables={}",
                template.getStatus(), template.getId(), command.getProjectId(),
                template.getComponents() != null ? template.getComponents().size() : 0,
                template.getVariables() != null ? template.getVariables().size() : 0);

        // Step 3: Draft-only? Return early
        if (command.isDraft()) {
            return templateMapper.mapToTemplateResponse(template);
        }

        // Step 4: Submit to Meta
        return metaSubmission.submitToMeta(template, payload, command.getWabaId());
    }
}
