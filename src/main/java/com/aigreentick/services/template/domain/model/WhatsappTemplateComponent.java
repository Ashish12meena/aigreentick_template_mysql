package com.aigreentick.services.template.domain.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;

import com.aigreentick.services.template.domain.enums.ComponentFormat;
import com.aigreentick.services.template.domain.enums.ComponentType;

@Entity
@Table(name = "whatsapp_template_components", uniqueConstraints = {
        @UniqueConstraint(name = "uk_template_component", columnNames = { "template_id", "component_type",
                "component_order" })
})
@Data
public class WhatsappTemplateComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private WhatsappTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false)
    private ComponentType componentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "format")
    private ComponentFormat format;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "media_handle", length = 2048)
    private String mediaHandle;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "add_security_recommendation")
    private Boolean addSecurityRecommendation = false;

    @Column(name = "code_expiration_minutes")
    private Integer codeExpirationMinutes;

    @Column(name = "component_order", nullable = false)
    private Integer componentOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "component", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WhatsappTemplateButton> buttons;

    @OneToOne(mappedBy = "component", cascade = CascadeType.ALL, orphanRemoval = true)
    private WhatsappTemplateExample example;

    @OneToMany(mappedBy = "component", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WhatsappTemplateCarouselCard> carouselCards;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
