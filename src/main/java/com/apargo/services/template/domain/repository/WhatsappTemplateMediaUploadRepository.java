package com.apargo.services.template.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;


import com.apargo.services.template.domain.model.WhatsappTemplateMediaUpload;

public interface WhatsappTemplateMediaUploadRepository extends JpaRepository<WhatsappTemplateMediaUpload, Long>{
    
}
