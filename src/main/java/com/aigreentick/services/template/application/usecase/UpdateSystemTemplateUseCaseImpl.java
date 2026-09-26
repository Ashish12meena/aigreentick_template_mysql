package com.aigreentick.services.template.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.api.request.BaseTemplateRequestDto;
import com.aigreentick.services.template.application.dto.command.SaveSystemTemplateCommand;
import com.aigreentick.services.template.application.dto.result.SystemTemplateDetailResult;
import com.aigreentick.services.template.application.mapper.SystemTemplateMapper;
import com.aigreentick.services.template.application.port.in.UpdateSystemTemplateUseCase;
import com.aigreentick.services.template.application.validation.TemplateValidationService;
import com.aigreentick.services.template.domain.model.SystemTemplate;
import com.aigreentick.services.template.domain.service.TemplateCommandService;
import com.aigreentick.services.template.domain.service.TemplateQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class UpdateSystemTemplateUseCaseImpl implements UpdateSystemTemplateUseCase {

    private final TemplateQueryService queryService;
    private final TemplateCommandService commandService;
    private final TemplateValidationService templateValidationService;
    private final SystemTemplateMapper systemTemplateMapper;

    /**
     * Full replace. Works on inactive entries too, so a deactivated template
     * can be fixed and re-activated. User templates copied from this entry are
     * independent rows and do not change.
     */
    @Override
    @Transactional
    public SystemTemplateDetailResult execute(Long systemTemplateId, SaveSystemTemplateCommand command) {
        BaseTemplateRequestDto template = command.getTemplateData();

        SystemTemplate systemTemplate = queryService.getSystemTemplate(systemTemplateId);

        templateValidationService.validate(template);
        commandService.ensureNoDuplicateSystemTemplate(
                template.getName(), template.getLanguage(), systemTemplateId);

        systemTemplateMapper.apply(command, systemTemplate);
        systemTemplate = commandService.saveSystemTemplate(systemTemplate);
        // @PreUpdate runs at flush; flush now so the response carries the new updatedAt.
        commandService.flush();

        log.info("Library template updated id={} name={} language={} active={}",
                systemTemplate.getId(), systemTemplate.getName(),
                systemTemplate.getLanguage(), systemTemplate.isActive());

        return systemTemplateMapper.toDetail(systemTemplate);
    }
}
