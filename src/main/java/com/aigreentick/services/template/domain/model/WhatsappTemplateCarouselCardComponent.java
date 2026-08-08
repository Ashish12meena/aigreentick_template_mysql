package com.aigreentick.services.template.domain.model;


import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;

import com.aigreentick.services.template.domain.enums.CardComponentFormat;
import com.aigreentick.services.template.domain.enums.CardComponentType;

@Entity
@Table(name = "whatsapp_template_carousel_card_components")
@Data
public class WhatsappTemplateCarouselCardComponent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private WhatsappTemplateCarouselCard card;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false)
    private CardComponentType componentType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "format")
    private CardComponentFormat format;
    
    @Column(name = "text", columnDefinition = "TEXT")
    private String text;
    
    @Column(name = "media_handle",length = 2048)
    private String mediaHandle;
    
    @Column(name = "media_url", length = 500)
    private String mediaUrl;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @OneToMany(mappedBy = "cardComponent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WhatsappTemplateCarouselButton> buttons;

    @OneToOne(mappedBy = "component", cascade = CascadeType.ALL, orphanRemoval = true)
    private WhatsappTemplateCarouselExample example;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

