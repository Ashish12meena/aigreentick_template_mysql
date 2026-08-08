package com.aigreentick.services.template.application.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.dto.request.create.BaseTemplateRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateButtonRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateButtonSupportedAppRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateCarouseCardComponentRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateCarouselButtonRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateCarouselCardRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateComponentRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateExampleRequestDto;
import com.aigreentick.services.template.api.dto.request.create.WhatsappTemplateVariablesRequestDto;
import com.aigreentick.services.template.application.dto.command.CreateTemplateCommand;
import com.aigreentick.services.template.application.dto.result.TemplateResult;
import com.aigreentick.services.template.domain.enums.CarouselButtonType;
import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.model.WhatsappTemplateButton;
import com.aigreentick.services.template.domain.model.WhatsappTemplateButtonSupportedApp;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselButton;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselCard;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselCardComponent;
import com.aigreentick.services.template.domain.model.WhatsappTemplateComponent;
import com.aigreentick.services.template.domain.model.WhatsappTemplateExample;
import com.aigreentick.services.template.domain.model.WhatsappTemplateVariable;
import com.aigreentick.services.template.domain.enums.ComponentFormat;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WhatsappTemplateMapper {

    // ─── Request DTO → Entity (for initial creation) ───

    public WhatsappTemplate mapToTemplateEntity(String payload, Long projectId, Long organizationId,
            CreateTemplateCommand command) {

        BaseTemplateRequestDto templateReq = command.getTemplateData();

        WhatsappTemplate template = new WhatsappTemplate();
        template.setProjectId(projectId);
        template.setOrganizationId(organizationId);
        template.setName(templateReq.getName());
        template.setCategory(templateReq.getCategory());
        template.setLanguage(templateReq.getLanguage());
        template.setWabaId(command.getWabaId());
        template.setStatus(TemplateStatus.DRAFT);
        template.setSubmissionPayload(payload);

        // Map components
        if (templateReq.getComponents() != null) {
            List<WhatsappTemplateComponent> components = mapComponents(templateReq.getComponents(), template);
            template.setComponents(components);

            // Apply media URLs from the request onto mapped components
            // applyMediaUrls(request.getMediaUrls(), components);
        }

        // Map variables
        if (command.getVariables() != null && !command.getVariables().isEmpty()) {
            List<WhatsappTemplateVariable> variables = mapVariables(command.getVariables(), template);
            template.setVariables(variables);
        }

        return template;
    }

    // ─── Media URL mapping ───

    /**
     * Applies media URLs from the request to already-mapped component entities.
     * <p>
     * TEMPLATE_HEADER      → sets mediaUrl on the HEADER component
     * CAROUSEL_CARD_HEADER → sets mediaUrl on the carousel card's HEADER card-component
     * <p>
     * Public so UpdateDraftTemplateUseCaseImpl can call it after rebuilding components.
     */
    // public void applyMediaUrls(List<MediaUrlMappingRequestDto> mediaUrls,
    //                             List<WhatsappTemplateComponent> components) {

    //     if (mediaUrls == null || mediaUrls.isEmpty() || components == null) return;

    //     for (MediaUrlMappingRequestDto mapping : mediaUrls) {
    //         if (mapping.getMediaUrl() == null || mapping.getMediaUrl().isBlank()) continue;

    //         switch (mapping.getLocation()) {

    //             case TEMPLATE_HEADER -> components.stream()
    //                     .filter(c -> c.getComponentType() == ComponentType.HEADER)
    //                     .findFirst()
    //                     .ifPresent(header -> header.setMediaUrl(mapping.getMediaUrl()));

    //             case CAROUSEL_CARD_HEADER -> components.stream()
    //                     .filter(c -> c.getComponentType() == ComponentType.CAROUSEL)
    //                     .findFirst()
    //                     .ifPresent(carousel -> applyCarouselCardMediaUrl(carousel, mapping));
    //         }
    //     }
    // }

    // private void applyCarouselCardMediaUrl(WhatsappTemplateComponent carousel,
    //                                         MediaUrlMappingRequestDto mapping) {

    //     if (carousel.getCarouselCards() == null) return;

    //     int targetCard = mapping.getCardIndex() != null ? mapping.getCardIndex() : 0;

    //     carousel.getCarouselCards().stream()
    //             .filter(card -> card.getCardIndex() == targetCard)
    //             .findFirst()
    //             .ifPresent(card -> {
    //                 if (card.getCardComponents() == null) return;

    //                 card.getCardComponents().stream()
    //                         .filter(cc -> cc.getComponentType() == CardComponentType.HEADER)
    //                         .findFirst()
    //                         .ifPresent(cc -> cc.setMediaUrl(mapping.getMediaUrl()));
    //             });
    // }

    // ─── Variable mapping ───

    public List<WhatsappTemplateVariable> mapVariables(
            List<WhatsappTemplateVariablesRequestDto> variableDtos,
            WhatsappTemplate template) {

        if (variableDtos == null || variableDtos.isEmpty()) {
            return new ArrayList<>();
        }

        List<WhatsappTemplateVariable> variables = new ArrayList<>();

        for (WhatsappTemplateVariablesRequestDto dto : variableDtos) {
            WhatsappTemplateVariable variable = WhatsappTemplateVariable.builder()
                    .template(template)
                    .componentType(dto.getComponentType())
                    .variableIndex(dto.getVariableIndex())
                    .label(dto.getLabel())
                    .labelValue(dto.getLabelValue())
                    .buttonIndex(dto.getButtonIndex() != null ? dto.getButtonIndex() : -1)
                    .cardIndex(dto.getCardIndex() != null ? dto.getCardIndex() : -1)
                    .build();

            variables.add(variable);
        }

        return variables;
    }

    // ─── Component mapping ───

    public List<WhatsappTemplateComponent> mapComponents(
            List<WhatsappTemplateComponentRequestDto> dtos, WhatsappTemplate template) {

        List<WhatsappTemplateComponent> components = new ArrayList<>();
        AtomicInteger order = new AtomicInteger(0);

        for (WhatsappTemplateComponentRequestDto dto : dtos) {
            WhatsappTemplateComponent comp = new WhatsappTemplateComponent();
            comp.setTemplate(template);
            comp.setComponentType(dto.getType());
            comp.setComponentOrder(order.getAndIncrement());

            if (dto.getFormat() != null) {
                comp.setFormat(dto.getFormat());
            }

            comp.setText(dto.getText());
            comp.setAddSecurityRecommendation(
                    dto.getAddSecurityRecommendation() != null ? dto.getAddSecurityRecommendation() : false);
            comp.setCodeExpirationMinutes(dto.getCodeExpirationMinutes());

            if (dto.getButtons() != null && !dto.getButtons().isEmpty()) {
                comp.setButtons(mapButtons(dto.getButtons(), comp));
            }

            if (dto.getExample() != null) {
                comp.setExample(mapExample(dto.getExample(), comp));
            }

            if (dto.getCards() != null && !dto.getCards().isEmpty()) {
                comp.setCarouselCards(mapCarouselCards(dto.getCards(), comp));
            }

            components.add(comp);
        }

        return components;
    }

    // ─── Button mapping ───

    private List<WhatsappTemplateButton> mapButtons(
            List<WhatsappTemplateButtonRequestDto> dtos, WhatsappTemplateComponent comp) {

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

            if (dto.getSupportedApps() != null && !dto.getSupportedApps().isEmpty()) {
                btn.setSupportedApps(mapSupportedApps(dto.getSupportedApps(), btn));
            }

            buttons.add(btn);
            idx++;
        }

        return buttons;
    }

    // ─── Example mapping ───

    private WhatsappTemplateExample mapExample(
            WhatsappTemplateExampleRequestDto dto,
            WhatsappTemplateComponent comp) {

        WhatsappTemplateExample ex = new WhatsappTemplateExample();
        ex.setComponent(comp);
        ex.setHeaderText(dto.getHeaderText());
        ex.setHeaderHandle(dto.getHeaderHandle());
        ex.setBodyText(dto.getBodyText());
        return ex;
    }

    private List<WhatsappTemplateButtonSupportedApp> mapSupportedApps(
            List<WhatsappTemplateButtonSupportedAppRequestDto> dtos, WhatsappTemplateButton btn) {

        List<WhatsappTemplateButtonSupportedApp> apps = new ArrayList<>();

        for (WhatsappTemplateButtonSupportedAppRequestDto dto : dtos) {
            WhatsappTemplateButtonSupportedApp app = new WhatsappTemplateButtonSupportedApp();
            app.setButton(btn);
            app.setPackageName(dto.getPackageName());
            app.setSignatureHash(dto.getSignatureHash());
            apps.add(app);
        }

        return apps;
    }

    // ─── Carousel mapping ───

    private List<WhatsappTemplateCarouselCard> mapCarouselCards(
            List<WhatsappTemplateCarouselCardRequestDto> dtos, WhatsappTemplateComponent comp) {

        List<WhatsappTemplateCarouselCard> cards = new ArrayList<>();
        int cardIdx = 0;

        for (WhatsappTemplateCarouselCardRequestDto dto : dtos) {
            WhatsappTemplateCarouselCard card = new WhatsappTemplateCarouselCard();
            card.setComponent(comp);
            card.setCardIndex(cardIdx++);

            if (dto.getComponents() != null) {
                card.setCardComponents(mapCardComponents(dto.getComponents(), card));
            }

            cards.add(card);
        }

        return cards;
    }

    private List<WhatsappTemplateCarouselCardComponent> mapCardComponents(
            List<WhatsappTemplateCarouseCardComponentRequestDto> dtos, WhatsappTemplateCarouselCard card) {

        List<WhatsappTemplateCarouselCardComponent> comps = new ArrayList<>();

        for (WhatsappTemplateCarouseCardComponentRequestDto dto : dtos) {
            WhatsappTemplateCarouselCardComponent cc = new WhatsappTemplateCarouselCardComponent();
            cc.setCard(card);
            cc.setComponentType(dto.getType());
            cc.setFormat(dto.getFormat());
            cc.setText(dto.getText());

            if (dto.getButtons() != null && !dto.getButtons().isEmpty()) {
                cc.setButtons(mapCarouselButtons(dto.getButtons(), cc));
            }

            comps.add(cc);
        }

        return comps;
    }

    private List<WhatsappTemplateCarouselButton> mapCarouselButtons(
            List<WhatsappTemplateCarouselButtonRequestDto> dtos, WhatsappTemplateCarouselCardComponent cc) {

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

    // ─── Entity → Response DTO ───

    public TemplateResult mapToTemplateResponse(WhatsappTemplate template) {
        return TemplateResult.builder()
                .id(template.getId())
                .name(template.getName())
                .status(template.getStatus())
                .category(template.getCategory())
                .language(template.getLanguage())
                .metaTemplateId(template.getMetaTemplateId())
                .build();
    }

    public TemplateResult mapToTemplateResponse(WhatsappTemplate template,
            String metaTemplateId, String status, String category) {
        return TemplateResult.builder()
                .id(template.getId())
                .name(template.getName())
                .status(TemplateStatus.valueOf(status.toUpperCase()))
                .category(category != null ? TemplateCategory.valueOf(category.toUpperCase()) : template.getCategory())
                .language(template.getLanguage())
                .metaTemplateId(metaTemplateId)
                .build();
    }
}