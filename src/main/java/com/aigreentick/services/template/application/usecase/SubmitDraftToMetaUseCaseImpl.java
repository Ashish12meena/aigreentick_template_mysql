package com.aigreentick.services.template.application.usecase;

import com.aigreentick.services.template.application.port.in.SubmitDraftToMetaUseCase;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.application.dto.result.TemplateResult;
import com.aigreentick.services.template.application.service.MetaTemplateSubmissionService;
import com.aigreentick.services.template.common.exception.InvalidTemplateStateException;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.service.TemplateCommandService;
import com.aigreentick.services.template.domain.service.TemplateQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubmitDraftToMetaUseCaseImpl implements SubmitDraftToMetaUseCase {

    private final TemplateQueryService queryService;
    private final TemplateCommandService commandService;
    private final MetaTemplateSubmissionService metaSubmission;

    /**
     * Submits an existing DRAFT template to Facebook for approval.
     * Reuses the stored submission payload.
     *
     * Not @Transactional for the same reason as CreateTemplateUseCaseImpl: the
     * DRAFT -> SUBMITTED move commits on its own before Meta is called, and
     * is guarded (only succeeds if the row is still a DRAFT), so two
     * concurrent submits of one draft cannot both reach Meta.
     */
    public TemplateResult execute(Long templateId, Long projectId) {
        log.info("Submitting draft to Meta: templateId={} projectId={}", templateId, projectId);

        WhatsappTemplate template = queryService.getDraftByIdAndProject(templateId, projectId);

        String payload = template.getSubmissionPayload();
        if (payload == null || payload.isBlank()) {
            throw new InvalidTemplateStateException(
                    "Template id=" + templateId + " has no submission payload. Update the draft first.");
        }

        // Clean 409 if another live template already took this name since the
        // draft was saved (the unique key would reject it anyway).
        commandService.ensureNoDuplicate(
                template.getWabaId(), template.getName(), template.getLanguage(), template.getId());

        commandService.markAsSubmitted(template);
        log.info("Template status -> SUBMITTED, templateId={}", template.getId());

        return metaSubmission.submitToMeta(template, payload, template.getWabaId());
    }
}
