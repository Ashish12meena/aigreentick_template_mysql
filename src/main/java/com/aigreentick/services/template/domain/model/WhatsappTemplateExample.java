package com.aigreentick.services.template.domain.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "whatsapp_template_examples")
@Data
public class WhatsappTemplateExample {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id", nullable = false, unique = true)
    private WhatsappTemplateComponent component;
    
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
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}