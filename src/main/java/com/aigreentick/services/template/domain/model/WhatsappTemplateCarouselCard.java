package com.aigreentick.services.template.domain.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "whatsapp_template_carousel_cards",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_component_card",
                           columnNames = {"component_id", "card_index"})
       })
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class WhatsappTemplateCarouselCard {
    
    @ToString.Include
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id", nullable = false)
    private WhatsappTemplateComponent component;
    
    @Column(name = "card_index", nullable = false)
    private Integer cardIndex;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WhatsappTemplateCarouselCardComponent> cardComponents;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
