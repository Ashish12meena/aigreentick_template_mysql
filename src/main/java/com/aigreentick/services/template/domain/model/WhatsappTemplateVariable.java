package com.aigreentick.services.template.domain.model;

import jakarta.persistence.*;
import lombok.*;

import com.aigreentick.services.template.domain.enums.VariableComponentType;

import java.time.Instant;

@Entity
@Table(
    name = "whatsapp_template_variables",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_var",
        columnNames = {"template_id", "component_type", "variable_index", "button_index", "card_index"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsappTemplateVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private WhatsappTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false)
    private VariableComponentType componentType;

    @Column(name = "variable_index", nullable = false)
    private Integer variableIndex;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "button_index", nullable = false)
    @Builder.Default
    private Integer buttonIndex = -1;

    @Column(name = "card_index", nullable = false)
    @Builder.Default
    private Integer cardIndex = -1;


    @Column(name = "label_value", length = 255)
    private String labelValue;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
