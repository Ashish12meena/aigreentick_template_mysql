package com.aigreentick.services.template.domain.model;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "whatsapp_template_carousel_examples")
@Data
public class WhatsappTemplateCarouselExample {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carousel_component_id", nullable = false, unique = true)
    private WhatsappTemplateCarouselCardComponent component;
    
    // Store as JSON - mirrors Facebook API exactly
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "header_text", columnDefinition = "JSON")
    private List<String> headerText;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "header_handle", columnDefinition = "JSON")
    private List<String> headerHandle;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body_text", columnDefinition = "JSON")
    private List<List<String>> bodyText;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
