package com.aigreentick.services.template.application.usecase;

import com.aigreentick.services.template.application.port.in.SubmitDraftToMetaUseCase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.application.dto.result.TemplateResult;
import com.aigreentick.services.template.application.service.MetaTemplateSubmissionService;
import com.aigreentick.services.template.common.exception.InvalidTemplateStateException;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.service.TemplateQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubmitDraftToMetaUseCaseImpl implements SubmitDraftToMetaUseCase {

    private final TemplateQueryService queryService;
    private final MetaTemplateSubmissionService metaSubmission;

    /**
     * Submits an existing DRAFT template to Facebook for approval.
     * Reuses the stored submission payload.
     */
    @Transactional
    public TemplateResult execute(Long templateId, Long projectId) {
        log.info("Submitting draft to Meta: templateId={} projectId={}", templateId, projectId);

        WhatsappTemplate template = queryService.getDraftByIdAndProject(templateId, projectId);

        String payload = template.getSubmissionPayload();
        if (payload == null || payload.isBlank()) {
            throw new InvalidTemplateStateException(
                    "Template id=" + templateId + " has no submission payload. Update the draft first.");
        }

        return metaSubmission.submitToMeta(template, payload, template.getWabaId());
    }
}