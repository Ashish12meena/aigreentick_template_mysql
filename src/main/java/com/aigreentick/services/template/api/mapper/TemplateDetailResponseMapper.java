package com.aigreentick.services.template.api.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.api.response.TemplateDetailResponseDto;
import com.aigreentick.services.template.application.dto.result.TemplateDetailResult;

/**
 * Maps the application layer's {@link TemplateDetailResult} to the REST
 * response shape. This never touches a JPA entity — the entity graph was
 * already flattened by {@code TemplateDetailResultMapper} inside the
 * use case's transaction, so there's no lazy-loading risk here regardless
 * of where or when this mapper runs.
 */
@Component
public class TemplateDetailResponseMapper {

    public TemplateDetailResponseDto mapToDetailResponse(TemplateDetailResult template) {
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
                .build();
    }

    private List<TemplateDetailResponseDto.ComponentDto> mapComponentsToDto(
            List<TemplateDetailResult.ComponentResult> components) {

        if (components == null || components.isEmpty()) {
            return List.of();
        }

        return components.stream().map(comp -> TemplateDetailResponseDto.ComponentDto.builder()
                .id(comp.getId())
                .componentType(comp.getComponentType() != null ? comp.getComponentType().name() : null)
                .format(comp.getFormat() != null ? comp.getFormat().name() : null)
                .text(comp.getText())
                .mediaHandle(comp.getMediaHandle())
                .mediaUrl(comp.getMediaUrl())
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
            List<TemplateDetailResult.ButtonResult> buttons) {

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
            List<TemplateDetailResult.SupportedAppResult> apps) {

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

    private TemplateDetailResponseDto.ExampleDto mapExampleToDto(TemplateDetailResult.ExampleResult example) {
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
            List<TemplateDetailResult.CarouselCardResult> cards) {

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
            List<TemplateDetailResult.CardComponentResult> cardComponents) {

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
            List<TemplateDetailResult.CarouselButtonResult> buttons) {

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
            List<TemplateDetailResult.VariableResult> variables) {

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