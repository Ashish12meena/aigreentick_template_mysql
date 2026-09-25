package com.aigreentick.services.template.domain.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
@Entity
@Table(name = "whatsapp_template_button_supported_apps")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class WhatsappTemplateButtonSupportedApp {
    
    @ToString.Include
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "button_id", nullable = false)
    private WhatsappTemplateButton button;
    
    @Column(name = "package_name", nullable = false, length = 150)
    private String packageName;
    
    @Column(name = "signature_hash", nullable = false, length = 150)
    private String signatureHash;
}

