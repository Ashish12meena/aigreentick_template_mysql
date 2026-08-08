package com.aigreentick.services.template.domain.model;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

import com.aigreentick.services.template.domain.enums.MediaType;
import com.aigreentick.services.template.domain.enums.UploadStatus;

@Entity
@Table(name = "whatsapp_template_media_uploads",
       indexes = {
           @Index(name = "idx_session", columnList = "session_id"),
           @Index(name = "idx_project", columnList = "project_id")
       })
@Data
public class WhatsappTemplateMediaUpload {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;
    
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    
    @Column(name = "waba_id", nullable = false, length = 255)
    private String wabaId;
    
    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;
    
    @Column(name = "media_handle")
    private String mediaHandle;
    
    @Column(name = "file_name", nullable = false)
    private String fileName;
    
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private UploadStatus status = UploadStatus.PENDING;
    
    @Column(name = "is_chunked_upload")
    private Boolean isChunkedUpload = false;
    
    @Column(name = "file_offset")
    private Long fileOffset = 0L;
    
    @Column(name = "upload_response", columnDefinition = "JSON")
    private String uploadResponse;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}