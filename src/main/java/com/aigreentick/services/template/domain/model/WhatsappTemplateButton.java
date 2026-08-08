package com.aigreentick.services.template.domain.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aigreentick.services.template.domain.enums.ButtonType;
import com.aigreentick.services.template.domain.enums.OtpType;

@Entity
@Table(name = "whatsapp_template_buttons", uniqueConstraints = {
        @UniqueConstraint(name = "uk_component_button", columnNames = { "component_id", "button_index" })
})
@Data
public class WhatsappTemplateButton {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id", nullable = false)
    private WhatsappTemplateComponent component;

    @Enumerated(EnumType.STRING)
    @Column(name = "button_type", nullable = false)
    private ButtonType buttonType;

    @Column(name = "text", nullable = false, length = 150)
    private String text;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "otp_type")
    private OtpType otpType;

    @Column(name = "button_index", nullable = false)
    private Integer buttonIndex;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "example", columnDefinition = "JSON")
    private List<String> example;

    @OneToMany(mappedBy = "button", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WhatsappTemplateButtonSupportedApp> supportedApps;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
