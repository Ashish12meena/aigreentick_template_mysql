package com.aigreentick.services.template.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "whatsapp_template_examples")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class WhatsappTemplateExample {
    
    @ToString.Include
    
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
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}