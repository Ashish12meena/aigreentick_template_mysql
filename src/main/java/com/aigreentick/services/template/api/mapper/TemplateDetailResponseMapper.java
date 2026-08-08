package com.aigreentick.services.template.api.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.dto.response.TemplateDetailResponseDto;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.model.WhatsappTemplateButton;
import com.aigreentick.services.template.domain.model.WhatsappTemplateButtonSupportedApp;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselButton;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselCard;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselCardComponent;
import com.aigreentick.services.template.domain.model.WhatsappTemplateComponent;
import com.aigreentick.services.template.domain.model.WhatsappTemplateExample;
import com.aigreentick.services.template.domain.model.WhatsappTemplateVariable;


@Component
public class TemplateDetailResponseMapper {

    public TemplateDetailResponseDto mapToDetailResponse(WhatsappTemplate template) {
        return TemplateDetailResponseDto.builder()
                .id(template.getId())
                .name(template.getName())
                .status(template.getStatus())
                .category(template.getCategory())
                .previousCategory(template.getPreviousCategory())
                .language(template.getLanguage())
                .metaTemplateId(template.getMetaTemplateId())
                .wabaId(template.getWabaId())
                .qualityRating(template.getQualityRating())
                .rejectionReason(template.getRejectionReason())
                .createdBy(template.getCreatedBy())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .components(mapComponentsToDto(template.getComponents()))
                .variables(mapVariablesToDto(template.getVariables()))
                .wabaId(template.getWabaId())
                .qualityRating(template.getQualityRating())
                .build();
    }

    private List<TemplateDetailResponseDto.ComponentDto> mapComponentsToDto(
            List<WhatsappTemplateComponent> components) {

        if (components == null || components.isEmpty()) {
            return List.of();
        }

        return components.stream().map(comp -> TemplateDetailResponseDto.ComponentDto.builder()
                .id(comp.getId())
                .componentType(comp.getComponentType() != null ? comp.getComponentType().name() : null)
                .format(comp.getFormat() != null ? comp.getFormat().name() : null)
                .text(comp.getText())
                .addSecurityRecommendation(comp.getAddSecurityRecommendation())
                .codeExpirationMinutes(comp.getCodeExpirationMinutes())
                .componentOrder(comp.getComponentOrder())
                .example(mapExampleToDto(comp.getExample()))
                .buttons(mapButtonsToDto(comp.getButtons()))
                .carouselCards(mapCarouselCardsToDto(comp.getCarouselCards()))
                .build()
        ).toList();
    }

    private List<TemplateDetailResponseDto.ButtonDto> mapButtonsToDto(
            List<WhatsappTemplateButton> buttons) {

        if (buttons == null || buttons.isEmpty()) {
            return List.of();
        }

        return buttons.stream().map(btn -> TemplateDetailResponseDto.ButtonDto.builder()
                .id(btn.getId())
                .buttonType(btn.getButtonType() != null ? btn.getButtonType().name() : null)
                .text(btn.getText())
                .url(btn.getUrl())
                .phoneNumber(btn.getPhoneNumber())
                .otpType(btn.getOtpType() != null ? btn.getOtpType().name() : null)
                .buttonIndex(btn.getButtonIndex())
                .example(btn.getExample())
                .supportedApps(mapSupportedAppsToDto(btn.getSupportedApps()))
                .build()
        ).toList();
    }

    private List<TemplateDetailResponseDto.SupportedAppDto> mapSupportedAppsToDto(
            List<WhatsappTemplateButtonSupportedApp> apps) {

        if (apps == null || apps.isEmpty()) {
            return List.of();
        }

        return apps.stream().map(app -> TemplateDetailResponseDto.SupportedAppDto.builder()
                .id(app.getId())
                .packageName(app.getPackageName())
                .signatureHash(app.getSignatureHash())
                .build()
        ).toList();
    }

    private TemplateDetailResponseDto.ExampleDto mapExampleToDto(WhatsappTemplateExample example) {
        if (example == null) {
            return null;
        }

        return TemplateDetailResponseDto.ExampleDto.builder()
                .id(example.getId())
                .headerText(example.getHeaderText())
                .headerHandle(example.getHeaderHandle())
                .bodyText(example.getBodyText())
                .build();
    }

    private List<TemplateDetailResponseDto.CarouselCardDto> mapCarouselCardsToDto(
            List<WhatsappTemplateCarouselCard> cards) {

        if (cards == null || cards.isEmpty()) {
            return List.of();
        }

        return cards.stream().map(card -> TemplateDetailResponseDto.CarouselCardDto.builder()
                .id(card.getId())
                .cardIndex(card.getCardIndex())
                .cardComponents(mapCardComponentsToDto(card.getCardComponents()))
                .build()
        ).toList();
    }

    private List<TemplateDetailResponseDto.CardComponentDto> mapCardComponentsToDto(
            List<WhatsappTemplateCarouselCardComponent> cardComponents) {

        if (cardComponents == null || cardComponents.isEmpty()) {
            return List.of();
        }

        return cardComponents.stream().map(cc -> TemplateDetailResponseDto.CardComponentDto.builder()
                .id(cc.getId())
                .componentType(cc.getComponentType() != null ? cc.getComponentType().name() : null)
                .format(cc.getFormat() != null ? cc.getFormat().name() : null)
                .text(cc.getText())
                .mediaHandle(cc.getMediaHandle())
                .mediaUrl(cc.getMediaUrl())
                .buttons(mapCarouselButtonsToDto(cc.getButtons()))
                .build()
        ).toList();
    }

    private List<TemplateDetailResponseDto.CarouselButtonDto> mapCarouselButtonsToDto(
            List<WhatsappTemplateCarouselButton> buttons) {

        if (buttons == null || buttons.isEmpty()) {
            return List.of();
        }

        return buttons.stream().map(btn -> TemplateDetailResponseDto.CarouselButtonDto.builder()
                .id(btn.getId())
                .buttonType(btn.getButtonType() != null ? btn.getButtonType().name() : null)
                .text(btn.getText())
                .url(btn.getUrl())
                .phoneNumber(btn.getPhoneNumber())
                .buttonIndex(btn.getButtonIndex())
                .build()
        ).toList();
    }

    private List<TemplateDetailResponseDto.VariableDto> mapVariablesToDto(
            List<WhatsappTemplateVariable> variables) {

        if (variables == null || variables.isEmpty()) {
            return List.of();
        }

        return variables.stream().map(v -> TemplateDetailResponseDto.VariableDto.builder()
                .id(v.getId())
                .componentType(v.getComponentType() != null ? v.getComponentType().name() : null)
                .variableIndex(v.getVariableIndex())
                .label(v.getLabel())
                .labelValue(v.getLabelValue())
                .buttonIndex(v.getButtonIndex())
                .cardIndex(v.getCardIndex())
                .build()
        ).toList();
    }  
}