package com.aigreentick.services.template.application.dto.result;

import java.time.LocalDateTime;
import java.util.List;

import com.aigreentick.services.template.domain.enums.ButtonType;
import com.aigreentick.services.template.domain.enums.CardComponentFormat;
import com.aigreentick.services.template.domain.enums.CardComponentType;
import com.aigreentick.services.template.domain.enums.CarouselButtonType;
import com.aigreentick.services.template.domain.enums.ComponentFormat;
import com.aigreentick.services.template.domain.enums.ComponentType;
import com.aigreentick.services.template.domain.enums.OtpType;
import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateQualityRating;
import com.aigreentick.services.template.domain.enums.TemplateStatus;
import com.aigreentick.services.template.domain.enums.VariableComponentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full per-template shape used by {@code GetTemplateUseCase.getById(...)} and
 * {@code getByNameAndLanguage(...)}. Built by {@link
 * com.aigreentick.services.template.application.mapper.TemplateDetailResultMapper}
 * while the use case's transaction is still open, so every lazy collection on
 * {@code WhatsappTemplate} is walked and flattened right here — the entity
 * itself never leaves the application layer. Kept separate from {@link
 * TemplateSummaryResult} because a detail read needs the full component /
 * button / carousel tree that a list row never carries.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TemplateDetailResult {

    private Long id;
    private String name;
    private TemplateStatus status;
    private TemplateCategory category;
    private TemplateCategory previousCategory;
    private String language;
    private String metaTemplateId;
    private String wabaId;
    private TemplateQualityRating qualityRating;
    private String rejectionReason;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<ComponentResult> components;
    private List<VariableResult> variables;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ComponentResult {
        private Long id;
        private ComponentType componentType;
        private ComponentFormat format;
        private String text;
        private String mediaHandle;
        private String mediaUrl;
        private Boolean addSecurityRecommendation;
        private Integer codeExpirationMinutes;
        private Integer componentOrder;

        private ExampleResult example;
        private List<ButtonResult> buttons;
        private List<CarouselCardResult> carouselCards;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ButtonResult {
        private Long id;
        private ButtonType buttonType;
        private String text;
        private String url;
        private String phoneNumber;
        private OtpType otpType;
        private Integer buttonIndex;
        private List<String> example;
        private List<SupportedAppResult> supportedApps;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class SupportedAppResult {
        private Long id;
        private String packageName;
        private String signatureHash;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ExampleResult {
        private Long id;
        private List<String> headerText;
        private List<String> headerHandle;
        private List<List<String>> bodyText;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CarouselCardResult {
        private Long id;
        private Integer cardIndex;
        private List<CardComponentResult> cardComponents;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CardComponentResult {
        private Long id;
        private CardComponentType componentType;
        private CardComponentFormat format;
        private String text;
        private String mediaHandle;
        private String mediaUrl;
        private List<CarouselButtonResult> buttons;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CarouselButtonResult {
        private Long id;
        private CarouselButtonType buttonType;
        private String text;
        private String url;
        private String phoneNumber;
        private Integer buttonIndex;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class VariableResult {
        private Long id;
        private VariableComponentType componentType;
        private Integer variableIndex;
        private String label;
        private String labelValue;
        private Integer buttonIndex;
        private Integer cardIndex;
    }
}