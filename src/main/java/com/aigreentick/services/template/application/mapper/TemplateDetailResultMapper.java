package com.aigreentick.services.template.application.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.application.dto.result.TemplateDetailResult;
import com.aigreentick.services.template.domain.model.WhatsappTemplate;
import com.aigreentick.services.template.domain.model.WhatsappTemplateButton;
import com.aigreentick.services.template.domain.model.WhatsappTemplateButtonSupportedApp;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselButton;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselCard;
import com.aigreentick.services.template.domain.model.WhatsappTemplateCarouselCardComponent;
import com.aigreentick.services.template.domain.model.WhatsappTemplateComponent;
import com.aigreentick.services.template.domain.model.WhatsappTemplateExample;
import com.aigreentick.services.template.domain.model.WhatsappTemplateVariable;

/**
 * Maps a fully-loaded WhatsappTemplate entity graph to the application
 * layer's own {@link TemplateDetailResult}. This lives in
 * {@code application.mapper}, not {@code api.mapper} — the use case that
 * fetches a template detail must not depend on anything under the
 * {@code api} package.
 *
 * <p>Deliberately called from inside the still-open
 * {@code @Transactional} use case: every lazy collection below
 * ({@code components}, {@code buttons}, {@code supportedApps},
 * {@code carouselCards}, {@code cardComponents}, {@code carouselButtons},
 * {@code variables}) is walked here, while the persistence context is
 * live, so the resulting {@link TemplateDetailResult} carries no lazy
 * proxies. Once it crosses into {@code api.mapper} it's a plain object
 * graph — no entity, no session required.
 */
@Component
public class TemplateDetailResultMapper {

    public TemplateDetailResult toDetailResult(WhatsappTemplate template) {
        return TemplateDetailResult.builder()
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
                .components(mapComponents(template.getComponents()))
                .variables(mapVariables(template.getVariables()))
                .build();
    }

    private List<TemplateDetailResult.ComponentResult> mapComponents(
            List<WhatsappTemplateComponent> components) {

        if (components == null || components.isEmpty()) {
            return List.of();
        }

        return components.stream().map(comp -> TemplateDetailResult.ComponentResult.builder()
                .id(comp.getId())
                .componentType(comp.getComponentType())
                .format(comp.getFormat())
                .text(comp.getText())
                .mediaHandle(comp.getMediaHandle())
                .mediaUrl(comp.getMediaUrl())
                .addSecurityRecommendation(comp.getAddSecurityRecommendation())
                .codeExpirationMinutes(comp.getCodeExpirationMinutes())
                .componentOrder(comp.getComponentOrder())
                .example(mapExample(comp.getExample()))
                .buttons(mapButtons(comp.getButtons()))
                .carouselCards(mapCarouselCards(comp.getCarouselCards()))
                .build()
        ).toList();
    }

    private List<TemplateDetailResult.ButtonResult> mapButtons(
            List<WhatsappTemplateButton> buttons) {

        if (buttons == null || buttons.isEmpty()) {
            return List.of();
        }

        return buttons.stream().map(btn -> TemplateDetailResult.ButtonResult.builder()
                .id(btn.getId())
                .buttonType(btn.getButtonType())
                .text(btn.getText())
                .url(btn.getUrl())
                .phoneNumber(btn.getPhoneNumber())
                .otpType(btn.getOtpType())
                .buttonIndex(btn.getButtonIndex())
                .example(btn.getExample())
                .supportedApps(mapSupportedApps(btn.getSupportedApps()))
                .build()
        ).toList();
    }

    private List<TemplateDetailResult.SupportedAppResult> mapSupportedApps(
            List<WhatsappTemplateButtonSupportedApp> apps) {

        if (apps == null || apps.isEmpty()) {
            return List.of();
        }

        return apps.stream().map(app -> TemplateDetailResult.SupportedAppResult.builder()
                .id(app.getId())
                .packageName(app.getPackageName())
                .signatureHash(app.getSignatureHash())
                .build()
        ).toList();
    }

    private TemplateDetailResult.ExampleResult mapExample(WhatsappTemplateExample example) {
        if (example == null) {
            return null;
        }

        return TemplateDetailResult.ExampleResult.builder()
                .id(example.getId())
                .headerText(example.getHeaderText())
                .headerHandle(example.getHeaderHandle())
                .bodyText(example.getBodyText())
                .build();
    }

    private List<TemplateDetailResult.CarouselCardResult> mapCarouselCards(
            List<WhatsappTemplateCarouselCard> cards) {

        if (cards == null || cards.isEmpty()) {
            return List.of();
        }

        return cards.stream().map(card -> TemplateDetailResult.CarouselCardResult.builder()
                .id(card.getId())
                .cardIndex(card.getCardIndex())
                .cardComponents(mapCardComponents(card.getCardComponents()))
                .build()
        ).toList();
    }

    private List<TemplateDetailResult.CardComponentResult> mapCardComponents(
            List<WhatsappTemplateCarouselCardComponent> cardComponents) {

        if (cardComponents == null || cardComponents.isEmpty()) {
            return List.of();
        }

        return cardComponents.stream().map(cc -> TemplateDetailResult.CardComponentResult.builder()
                .id(cc.getId())
                .componentType(cc.getComponentType())
                .format(cc.getFormat())
                .text(cc.getText())
                .mediaHandle(cc.getMediaHandle())
                .mediaUrl(cc.getMediaUrl())
                .buttons(mapCarouselButtons(cc.getButtons()))
                .build()
        ).toList();
    }

    private List<TemplateDetailResult.CarouselButtonResult> mapCarouselButtons(
            List<WhatsappTemplateCarouselButton> buttons) {

        if (buttons == null || buttons.isEmpty()) {
            return List.of();
        }

        return buttons.stream().map(btn -> TemplateDetailResult.CarouselButtonResult.builder()
                .id(btn.getId())
                .buttonType(btn.getButtonType())
                .text(btn.getText())
                .url(btn.getUrl())
                .phoneNumber(btn.getPhoneNumber())
                .buttonIndex(btn.getButtonIndex())
                .build()
        ).toList();
    }

    private List<TemplateDetailResult.VariableResult> mapVariables(
            List<WhatsappTemplateVariable> variables) {

        if (variables == null || variables.isEmpty()) {
            return List.of();
        }

        return variables.stream().map(v -> TemplateDetailResult.VariableResult.builder()
                .id(v.getId())
                .componentType(v.getComponentType())
                .variableIndex(v.getVariableIndex())
                .label(v.getLabel())
                .labelValue(v.getLabelValue())
                .buttonIndex(v.getButtonIndex())
                .cardIndex(v.getCardIndex())
                .build()
        ).toList();
    }
}