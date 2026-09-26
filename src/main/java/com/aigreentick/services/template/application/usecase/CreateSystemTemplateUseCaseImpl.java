package com.aigreentick.services.template.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.api.request.BaseTemplateRequestDto;
import com.aigreentick.services.template.application.dto.command.SaveSystemTemplateCommand;
import com.aigreentick.services.template.application.dto.result.SystemTemplateDetailResult;
import com.aigreentick.services.template.application.mapper.SystemTemplateMapper;
import com.aigreentick.services.template.application.port.in.CreateSystemTemplateUseCase;
import com.aigreentick.services.template.application.validation.TemplateValidationService;
import com.aigreentick.services.template.domain.model.SystemTemplate;
import com.aigreentick.services.template.domain.service.TemplateCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreateSystemTemplateUseCaseImpl implements CreateSystemTemplateUseCase {

    private final TemplateCommandService commandService;
    private final TemplateValidationService templateValidationService;
    private final SystemTemplateMapper systemTemplateMapper;

    /**
     * Validates with the same Meta rules as a user's template, so a library
     * entry that is broken fails here rather than for every user who copies it.
     */
    @Override
    @Transactional
    public SystemTemplateDetailResult execute(SaveSystemTemplateCommand command) {
        BaseTemplateRequestDto template = command.getTemplateData();

        templateValidationService.validate(template);
        commandService.ensureNoDuplicateSystemTemplate(template.getName(), template.getLanguage(), null);

        SystemTemplate systemTemplate = new SystemTemplate();
        systemTemplateMapper.apply(command, systemTemplate);
        systemTemplate = commandService.saveSystemTemplate(systemTemplate);
        commandService.flush();

        log.info("Library template created id={} name={} language={} category={}",
                systemTemplate.getId(), systemTemplate.getName(),
                systemTemplate.getLanguage(), systemTemplate.getCategory());

        return systemTemplateMapper.toDetail(systemTemplate);
    }
}
