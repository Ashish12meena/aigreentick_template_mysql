package com.aigreentick.services.template.application.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateButtonRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateCarouseCardComponentRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateCarouselButtonRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateCarouselCardRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateComponentRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateExampleRequestDto;
import com.aigreentick.services.template.api.dto.request.sync.SyncTemplateRequest;
import com.aigreentick.services.template.domain.enums.CarouselButtonType;
import com.aigreentick.services.template.domain.enums.ComponentFormat;
import com.aigreentick.services.template.domain.enums.ComponentType;
import com.aigreentick.services.template.domain.enums.VariableComponentType;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.model.WhatsappTemplateButton;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselButton;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselCard;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselCardComponent;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselExample;
import com.aigreentick.services.template.domain.model.WhatsappTemplateComponent;
import com.aigreentick.services.template.domain.model.WhatsappTemplateExample;
import com.aigreentick.services.template.domain.model.WhatsappTemplateVariable;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TemplateSyncMapper {

    // Matches {{1}}, {{2}}, etc.
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\d+)}}");

    /**
     * Maps Facebook template sync request to WhatsappTemplate entity
     */
    public WhatsappTemplate fromFacebookTemplate(
            SyncTemplateRequest fbTemplate,
            Long projectId,
            Long organizationId,
            String wabaId) {

        WhatsappTemplate template = new WhatsappTemplate();
        template.setProjectId(projectId);
        template.setOrganizationId(organizationId);
        template.setWabaId(wabaId);
        template.setName(fbTemplate.getName());
        template.setLanguage(fbTemplate.getLanguage());
        template.setStatus(fbTemplate.getStatus());
        template.setMetaTemplateId(fbTemplate.getMetaTemplateId());
        template.setRejectionReason(fbTemplate.getRejectionReason());

        if (fbTemplate.getCategory() != null) {
            try {
                template.setCategory(
                        com.aigreentick.services.template.domain.enums.TemplateCategory
                                .valueOf(fbTemplate.getCategory().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid category from Facebook: {}", fbTemplate.getCategory());
            }
        }

        if (fbTemplate.getPreviousCategory() != null) {
            try {
                template.setPreviousCategory(
                        com.aigreentick.services.template.domain.enums.TemplateCategory
                                .valueOf(fbTemplate.getPreviousCategory().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid previous category from Facebook: {}", fbTemplate.getPreviousCategory());
            }
        }

        List<WhatsappTemplateVariable> variables = new ArrayList<>();

        if (fbTemplate.getComponents() != null && !fbTemplate.getComponents().isEmpty()) {
            List<WhatsappTemplateComponent> components = mapComponents(
                    fbTemplate.getComponents(), template, variables);
            template.setComponents(components);
        }

        if (!variables.isEmpty()) {
            template.setVariables(variables);
            log.info("Extracted {} variables from Facebook template: {}", variables.size(), fbTemplate.getName());
        }

        return template;
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPONENT MAPPING
    // ═══════════════════════════════════════════════════════════════

    private List<WhatsappTemplateComponent> mapComponents(
            List<WhatsappTemplateComponentRequestDto> dtos,
            WhatsappTemplate template,
            List<WhatsappTemplateVariable> variables) {

        List<WhatsappTemplateComponent> components = new ArrayList<>();
        AtomicInteger order = new AtomicInteger(0);

        for (WhatsappTemplateComponentRequestDto dto : dtos) {
            WhatsappTemplateComponent comp = new WhatsappTemplateComponent();
            comp.setTemplate(template);
            comp.setComponentType(dto.getType());
            comp.setComponentOrder(order.getAndIncrement());

            if (dto.getFormat() != null) {
                try {
                    comp.setFormat(dto.getFormat());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid component format: {}", dto.getFormat());
                }
            }

            comp.setText(dto.getText());
            comp.setAddSecurityRecommendation(
                    dto.getAddSecurityRecommendation() != null ? dto.getAddSecurityRecommendation() : false);
            comp.setCodeExpirationMinutes(dto.getCodeExpirationMinutes());

            // Map buttons
            if (dto.getButtons() != null && !dto.getButtons().isEmpty()) {
                comp.setButtons(mapButtons(dto.getButtons(), comp));
                extractButtonVariables(dto.getButtons(), template, variables);
            }

            // Map example (always persisted for Facebook data)
            if (dto.getExample() != null) {
                comp.setExample(mapExample(dto.getExample(), comp));
            }

            if (dto.getExample() != null
                    && dto.getExample().getHeaderHandle() != null
                    && !dto.getExample().getHeaderHandle().isEmpty()
                    && comp.getFormat() != null
                    && (comp.getFormat() == ComponentFormat.IMAGE
                            || comp.getFormat() == ComponentFormat.VIDEO
                            || comp.getFormat() == ComponentFormat.DOCUMENT)) {

                comp.setMediaHandle(dto.getExample().getHeaderHandle().get(0));
            }

            // Extract variables ONLY where {{n}} placeholders exist in text
            extractComponentVariables(dto, template, variables);

            // Map carousel cards
            if (dto.getCards() != null && !dto.getCards().isEmpty()) {
                comp.setCarouselCards(mapCarouselCards(dto.getCards(), comp, template, variables));
            }

            components.add(comp);
        }

        return components;
    }

    // ═══════════════════════════════════════════════════════════════
    // EXAMPLE MAPPING (always stored — separate from variable extraction)
    // ═══════════════════════════════════════════════════════════════

    private WhatsappTemplateExample mapExample(
            WhatsappTemplateExampleRequestDto dto,
            WhatsappTemplateComponent comp) {

        WhatsappTemplateExample example = new WhatsappTemplateExample();
        example.setComponent(comp);
        example.setHeaderText(dto.getHeaderText());
        example.setHeaderHandle(dto.getHeaderHandle());
        example.setBodyText(dto.getBodyText());
        return example;
    }

    private WhatsappTemplateCarouselExample mapCarouselExample(
            WhatsappTemplateExampleRequestDto dto,
            WhatsappTemplateCarouselCardComponent cc) {

        WhatsappTemplateCarouselExample example = new WhatsappTemplateCarouselExample();
        example.setComponent(cc);
        example.setHeaderText(dto.getHeaderText());
        example.setHeaderHandle(dto.getHeaderHandle());
        example.setBodyText(dto.getBodyText());
        return example;
    }

    // ═══════════════════════════════════════════════════════════════
    // VARIABLE EXTRACTION — only when {{n}} exists in text
    // ═══════════════════════════════════════════════════════════════

    /**
     * Counts how many {{n}} placeholders exist in the text.
     * Returns 0 if text is null or has no placeholders.
     */
    private int countPlaceholders(String text) {
        if (text == null || text.isBlank())
            return 0;
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find())
            count++;
        return count;
    }

    /**
     * Extracts variables from HEADER and BODY components.
     *
     * Only extracts when the component's text contains {{1}}, {{2}} etc.
     * - HEADER (TEXT format) with {{1}} in text → example.header_text provides
     * labels
     * - BODY with {{1}}, {{2}} in text → example.body_text[0] provides labels
     * - HEADER with IMAGE/VIDEO/DOCUMENT → header_handle is media, NOT a variable
     */
    private void extractComponentVariables(
            WhatsappTemplateComponentRequestDto dto,
            WhatsappTemplate template,
            List<WhatsappTemplateVariable> variables) {

        String text = dto.getText();
        int placeholderCount = countPlaceholders(text);

        // No placeholders in text → no variables to extract
        if (placeholderCount == 0)
            return;

        WhatsappTemplateExampleRequestDto example = dto.getExample();

        if (dto.getType() == ComponentType.HEADER) {
            // Only TEXT format headers have {{n}} variables
            // IMAGE/VIDEO/DOCUMENT headers use header_handle which is media, not a variable
            if (example != null && example.getHeaderText() != null && !example.getHeaderText().isEmpty()) {
                List<String> headerExamples = example.getHeaderText();
                for (int i = 0; i < Math.min(placeholderCount, headerExamples.size()); i++) {
                    String value = headerExamples.get(i);
                    variables.add(buildVariable(
                            template, VariableComponentType.HEADER, i + 1, value, -1, -1));
                }
            } else {
                // No example provided, still create variables with null label
                for (int i = 0; i < placeholderCount; i++) {
                    variables.add(buildVariable(
                            template, VariableComponentType.HEADER, i + 1, null, -1, -1));
                }
            }
        }

        if (dto.getType() == ComponentType.BODY) {
            if (example != null && example.getBodyText() != null && !example.getBodyText().isEmpty()) {
                List<String> bodyExamples = example.getBodyText().get(0);
                if (bodyExamples != null) {
                    for (int i = 0; i < Math.min(placeholderCount, bodyExamples.size()); i++) {
                        String value = bodyExamples.get(i);
                        variables.add(buildVariable(
                                template, VariableComponentType.BODY, i + 1, value, -1, -1));
                    }
                }
            } else {
                for (int i = 0; i < placeholderCount; i++) {
                    variables.add(buildVariable(
                            template, VariableComponentType.BODY, i + 1, null, -1, -1));
                }
            }
        }
    }

    /**
     * Extracts variables from button examples.
     * Only URL buttons with {{n}} in the URL have variables.
     */
    private void extractButtonVariables(
            List<WhatsappTemplateButtonRequestDto> buttons,
            WhatsappTemplate template,
            List<WhatsappTemplateVariable> variables) {

        int btnIdx = 0;
        for (WhatsappTemplateButtonRequestDto btn : buttons) {
            int actualBtnIdx = btn.getIndex() != null ? btn.getIndex() : btnIdx;

            // Check if URL contains {{n}} placeholders
            int placeholderCount = countPlaceholders(btn.getUrl());

            if (placeholderCount > 0 && btn.getExample() != null && !btn.getExample().isEmpty()) {
                List<String> btnExamples = btn.getExample();
                for (int i = 0; i < Math.min(placeholderCount, btnExamples.size()); i++) {
                    String value = btnExamples.get(i);
                    variables.add(buildVariable(
                            template, VariableComponentType.BUTTON, i + 1, value, actualBtnIdx, -1));
                }
            } else if (placeholderCount > 0) {
                // Placeholders exist but no example
                for (int i = 0; i < placeholderCount; i++) {
                    variables.add(buildVariable(
                            template, VariableComponentType.BUTTON, i + 1, null, actualBtnIdx, -1));
                }
            }

            btnIdx++;
        }
    }

    /**
     * Extracts variables from carousel card component examples.
     * Only when text contains {{n}} placeholders.
     */
    private void extractCarouselCardVariables(
            WhatsappTemplateCarouseCardComponentRequestDto dto,
            int cardIndex,
            WhatsappTemplate template,
            List<WhatsappTemplateVariable> variables) {

        String text = dto.getText();
        int placeholderCount = countPlaceholders(text);

        if (placeholderCount == 0)
            return;

        WhatsappTemplateExampleRequestDto example = dto.getExample();

        if (dto.getType() == com.aigreentick.services.template.domain.enums.CardComponentType.HEADER) {
            if (example != null && example.getHeaderText() != null && !example.getHeaderText().isEmpty()) {
                List<String> headerExamples = example.getHeaderText();
                for (int i = 0; i < Math.min(placeholderCount, headerExamples.size()); i++) {
                    variables.add(buildVariable(
                            template, VariableComponentType.HEADER, i + 1,
                            headerExamples.get(i), -1, cardIndex));
                }
            } else {
                for (int i = 0; i < placeholderCount; i++) {
                    variables.add(buildVariable(
                            template, VariableComponentType.HEADER, i + 1, null, -1, cardIndex));
                }
            }
        }

        if (dto.getType() == com.aigreentick.services.template.domain.enums.CardComponentType.BODY) {
            if (example != null && example.getBodyText() != null && !example.getBodyText().isEmpty()) {
                List<String> bodyExamples = example.getBodyText().get(0);
                if (bodyExamples != null) {
                    for (int i = 0; i < Math.min(placeholderCount, bodyExamples.size()); i++) {
                        variables.add(buildVariable(
                                template, VariableComponentType.BODY, i + 1,
                                bodyExamples.get(i), -1, cardIndex));
                    }
                }
            } else {
                for (int i = 0; i < placeholderCount; i++) {
                    variables.add(buildVariable(
                            template, VariableComponentType.BODY, i + 1, null, -1, cardIndex));
                }
            }
        }
    }

    /**
     * Extracts variables from carousel button examples.
     * Only URL buttons with {{n}} in the URL.
     */
    private void extractCarouselButtonVariables(
            List<WhatsappTemplateCarouselButtonRequestDto> buttons,
            int cardIndex,
            WhatsappTemplate template,
            List<WhatsappTemplateVariable> variables) {

        int btnIdx = 0;
        for (WhatsappTemplateCarouselButtonRequestDto btn : buttons) {
            int actualBtnIdx = btn.getIndex() != null ? btn.getIndex() : btnIdx;

            int placeholderCount = countPlaceholders(btn.getUrl());

            if (placeholderCount > 0 && btn.getExample() != null && !btn.getExample().isEmpty()) {
                List<String> btnExamples = btn.getExample();
                for (int i = 0; i < Math.min(placeholderCount, btnExamples.size()); i++) {
                    variables.add(buildVariable(
                            template, VariableComponentType.BUTTON, i + 1,
                            btnExamples.get(i), actualBtnIdx, cardIndex));
                }
            } else if (placeholderCount > 0) {
                for (int i = 0; i < placeholderCount; i++) {
                    variables.add(buildVariable(
                            template, VariableComponentType.BUTTON, i + 1, null, actualBtnIdx, cardIndex));
                }
            }

            btnIdx++;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VARIABLE BUILDER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Builds a WhatsappTemplateVariable.
     * For synced templates: label = labelValue = example value (no user-defined key
     * from Facebook)
     */
    private WhatsappTemplateVariable buildVariable(
            WhatsappTemplate template,
            VariableComponentType componentType,
            int variableIndex,
            String value,
            int buttonIndex,
            int cardIndex) {

        return WhatsappTemplateVariable.builder()
                .template(template)
                .componentType(componentType)
                .variableIndex(variableIndex)
                .label(value)
                .labelValue(value)
                .buttonIndex(buttonIndex) // -1 = not a button variable
                .cardIndex(cardIndex) // -1 = not a carousel variable
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // BUTTON MAPPING
    // ═══════════════════════════════════════════════════════════════

    private List<WhatsappTemplateButton> mapButtons(
            List<WhatsappTemplateButtonRequestDto> dtos,
            WhatsappTemplateComponent comp) {

        List<WhatsappTemplateButton> buttons = new ArrayList<>();
        int idx = 0;

        for (WhatsappTemplateButtonRequestDto dto : dtos) {
            WhatsappTemplateButton btn = new WhatsappTemplateButton();
            btn.setComponent(comp);
            btn.setButtonType(dto.getType());
            btn.setText(dto.getText());
            btn.setUrl(dto.getUrl());
            btn.setPhoneNumber(dto.getPhoneNumber());
            btn.setOtpType(dto.getOtpType());
            btn.setButtonIndex(dto.getIndex() != null ? dto.getIndex() : idx);
            btn.setExample(dto.getExample());

            buttons.add(btn);
            idx++;
        }

        return buttons;
    }

    // ═══════════════════════════════════════════════════════════════
    // CAROUSEL MAPPING
    // ═══════════════════════════════════════════════════════════════

    private List<WhatsappTemplateCarouselCard> mapCarouselCards(
            List<WhatsappTemplateCarouselCardRequestDto> dtos,
            WhatsappTemplateComponent comp,
            WhatsappTemplate template,
            List<WhatsappTemplateVariable> variables) {

        List<WhatsappTemplateCarouselCard> cards = new ArrayList<>();
        int cardIdx = 0;

        for (WhatsappTemplateCarouselCardRequestDto dto : dtos) {
            WhatsappTemplateCarouselCard card = new WhatsappTemplateCarouselCard();
            card.setComponent(comp);
            card.setCardIndex(cardIdx);

            if (dto.getComponents() != null) {
                card.setCardComponents(
                        mapCardComponents(dto.getComponents(), card, cardIdx, template, variables));
            }

            cards.add(card);
            cardIdx++;
        }

        return cards;
    }

    private List<WhatsappTemplateCarouselCardComponent> mapCardComponents(
            List<WhatsappTemplateCarouseCardComponentRequestDto> dtos,
            WhatsappTemplateCarouselCard card,
            int cardIndex,
            WhatsappTemplate template,
            List<WhatsappTemplateVariable> variables) {

        List<WhatsappTemplateCarouselCardComponent> comps = new ArrayList<>();

        for (WhatsappTemplateCarouseCardComponentRequestDto dto : dtos) {
            WhatsappTemplateCarouselCardComponent cc = new WhatsappTemplateCarouselCardComponent();
            cc.setCard(card);
            cc.setComponentType(dto.getType());
            cc.setFormat(dto.getFormat());
            cc.setText(dto.getText());

            // Map carousel buttons
            if (dto.getButtons() != null && !dto.getButtons().isEmpty()) {
                cc.setButtons(mapCarouselButtons(dto.getButtons(), cc));
                extractCarouselButtonVariables(dto.getButtons(), cardIndex, template, variables);
            }

            // Map carousel example (always persisted)
            if (dto.getExample() != null) {
                cc.setExample(mapCarouselExample(dto.getExample(), cc));
            }

            if (dto.getExample() != null
                    && dto.getExample().getHeaderHandle() != null
                    && !dto.getExample().getHeaderHandle().isEmpty()
                    && cc.getFormat() != null) {

                cc.setMediaHandle(dto.getExample().getHeaderHandle().get(0));
            }

            // Extract variables only where {{n}} exists in text
            extractCarouselCardVariables(dto, cardIndex, template, variables);

            comps.add(cc);
        }

        return comps;
    }

    private List<WhatsappTemplateCarouselButton> mapCarouselButtons(
            List<WhatsappTemplateCarouselButtonRequestDto> dtos,
            WhatsappTemplateCarouselCardComponent cc) {

        List<WhatsappTemplateCarouselButton> buttons = new ArrayList<>();
        int idx = 0;

        for (WhatsappTemplateCarouselButtonRequestDto dto : dtos) {
            WhatsappTemplateCarouselButton btn = new WhatsappTemplateCarouselButton();
            btn.setCardComponent(cc);
            btn.setButtonType(CarouselButtonType.valueOf(dto.getType().name()));
            btn.setText(dto.getText());
            btn.setUrl(dto.getUrl());
            btn.setPhoneNumber(dto.getPhoneNumber());
            btn.setButtonIndex(dto.getIndex() != null ? dto.getIndex() : idx);
            buttons.add(btn);
            idx++;
        }

        return buttons;
    }
}