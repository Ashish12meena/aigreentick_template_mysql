package com.aigreentick.services.template.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.aigreentick.services.template.domain.enums.TemplateCategory;
import com.aigreentick.services.template.domain.enums.TemplateQualityRating;
import com.aigreentick.services.template.domain.enums.TemplateStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TemplateDetailResponseDto {

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

    private List<ComponentDto> components;
    private List<VariableDto> variables;

    // ─── Component ───
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ComponentDto {
        private Long id;
        private String componentType;
        private String format;
        private String text;
        private Boolean addSecurityRecommendation;
        private Integer codeExpirationMinutes;
        private Integer componentOrder;

        private ExampleDto example;
        private List<ButtonDto> buttons;
        private List<CarouselCardDto> carouselCards;
    }

    // ─── Button ───
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ButtonDto {
        private Long id;
        private String buttonType;
        private String text;
        private String url;
        private String phoneNumber;
        private String otpType;
        private Integer buttonIndex;
        private List<String> example;
        private List<SupportedAppDto> supportedApps;
    }

    // ─── Supported App (OTP Autofill) ───
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class SupportedAppDto {
        private Long id;
        private String packageName;
        private String signatureHash;
    }

    // ─── Example ───
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ExampleDto {
        private Long id;
        private List<String> headerText;
        private List<String> headerHandle;
        private List<List<String>> bodyText;
    }

    // ─── Carousel Card ───
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CarouselCardDto {
        private Long id;
        private Integer cardIndex;
        private List<CardComponentDto> cardComponents;
    }

    // ─── Card Component ───
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CardComponentDto {
        private Long id;
        private String componentType;
        private String format;
        private String text;
        private String mediaHandle;
        private String mediaUrl;
        private List<CarouselButtonDto> buttons;
    }

    // ─── Carousel Button ───
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CarouselButtonDto {
        private Long id;
        private String buttonType;
        private String text;
        private String url;
        private String phoneNumber;
        private Integer buttonIndex;
    }

    // ─── Variable ───
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class VariableDto {
        private Long id;
        private String componentType;
        private Integer variableIndex;
        private String label;
        private String labelValue;
        private Integer buttonIndex;
        private Integer cardIndex;
    }
}