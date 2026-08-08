package com.aigreentick.services.template.domain.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

import com.aigreentick.services.template.domain.enums.CarouselButtonType;

@Entity
@Table(name = "whatsapp_template_carousel_buttons",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_card_button",
                           columnNames = {"card_component_id", "button_index"})
       })
@Data
public class WhatsappTemplateCarouselButton {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_component_id", nullable = false)
    private WhatsappTemplateCarouselCardComponent cardComponent;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "button_type", nullable = false)
    private CarouselButtonType buttonType;
    
    @Column(name = "text", nullable = false, length = 150)
    private String text;
    
    @Column(name = "url", length = 500)
    private String url;
    
    @Column(name = "phone_number", length = 30)
    private String phoneNumber;
    
    @Column(name = "button_index", nullable = false)
    private Integer buttonIndex;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

