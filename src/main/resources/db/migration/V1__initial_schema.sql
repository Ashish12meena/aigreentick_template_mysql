drop schema apargo_wa_template;

create schema apargo_wa_template;

use apargo_wa_template;
-- WHATSAPP TEMPLATE SERVICE — ALL TABLES (MYSQL)
-- 1. whatsapp_templates
-- Root entity – one row per template per project per WABA

CREATE TABLE whatsapp_templates (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  organization_id BIGINT UNSIGNED NOT NULL,
  project_id BIGINT UNSIGNED NOT NULL,
  waba_id VARCHAR(255)  NOT NULL,

  name VARCHAR(150) NOT NULL,
  category ENUM('MARKETING','UTILITY','AUTHENTICATION') NOT NULL,
  language VARCHAR(10) NOT NULL,

  status ENUM('DRAFT','NEW_CREATED','SUBMITTED','PENDING','APPROVED','REJECTED','PAUSED','DISABLED','FAILED') DEFAULT 'PENDING',
  rejection_reason TEXT NULL,
  previous_category ENUM('MARKETING','UTILITY','AUTHENTICATION') NULL,

  meta_template_id VARCHAR(150) NULL,

  quality_rating ENUM('GREEN','YELLOW','RED','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',

  submission_payload JSON NULL,
  meta_response JSON NULL,

  created_by BIGINT UNSIGNED NULL,

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,

  UNIQUE KEY uk_project_template (
    project_id, name, language
  ),

  INDEX idx_project_status (project_id, status),
  INDEX idx_waba_id (waba_id)
) ENGINE=InnoDB;


-- 2. whatsapp_template_components
-- HEADER / BODY / FOOTER / BUTTONS / CAROUSEL / LTO

CREATE TABLE whatsapp_template_components (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  template_id BIGINT UNSIGNED NOT NULL,

  component_type ENUM(
    'HEADER','BODY','FOOTER',
    'BUTTONS','CAROUSEL','LIMITED_TIME_OFFER'
  ) NOT NULL,

  format ENUM(
    'TEXT','IMAGE','VIDEO','DOCUMENT','LOCATION','PRODUCT'
  ) NULL,

  text TEXT NULL,

  media_handle VARCHAR(2048) NULL,

  media_url VARCHAR(500) NULL,



  add_security_recommendation TINYINT(1) DEFAULT 0,

  code_expiration_minutes INT NULL,

  component_order INT NOT NULL,

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uk_template_component (
    template_id, component_type, component_order
  ),

  FOREIGN KEY (template_id)
    REFERENCES whatsapp_templates(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;


-- 3. whatsapp_template_buttons
-- Buttons for normal BUTTONS component (non-carousel)

CREATE TABLE whatsapp_template_buttons (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  component_id BIGINT UNSIGNED NOT NULL,

  button_type ENUM(
    'URL','QUICK_REPLY','PHONE_NUMBER',
    'COPY_CODE','CATALOG','MPM','SPM','OTP'
  ) NOT NULL,

  text VARCHAR(150) NOT NULL,
  url VARCHAR(500) NULL,
  phone_number VARCHAR(30) NULL,

  otp_type ENUM('ONE_TAP','COPY_CODE','ZERO_TAP') NULL,

  button_index INT NOT NULL,
  
  example JSON NULL COMMENT 'Direct array like ["ORDER123", "ORDER456"]',

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uk_component_button (
    component_id, button_index
  ),

  FOREIGN KEY (component_id)
    REFERENCES whatsapp_template_components(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;


-- 4. whatsapp_template_button_supported_apps
-- OTP Autofill (Android apps)
CREATE TABLE whatsapp_template_button_supported_apps (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  button_id BIGINT UNSIGNED NOT NULL,
  package_name VARCHAR(150) NOT NULL,
  signature_hash VARCHAR(150) NOT NULL,

  FOREIGN KEY (button_id)
    REFERENCES whatsapp_template_buttons(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

-- 5. whatsapp_template_examples
-- Variable examples used in Meta validation
CREATE TABLE whatsapp_template_examples (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  component_id BIGINT UNSIGNED NOT NULL,

  header_text JSON NULL COMMENT 'List<String> for header text variables',
  header_handle JSON NULL COMMENT 'List<String> for header media handles',
  body_text JSON NULL COMMENT 'List<List<String>> for body text variables',

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  FOREIGN KEY (component_id)
    REFERENCES whatsapp_template_components(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;
-- 6. whatsapp_template_carousel_cards

CREATE TABLE whatsapp_template_carousel_cards (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  component_id BIGINT UNSIGNED NOT NULL,
  card_index INT NOT NULL,

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uk_component_card (
    component_id, card_index
  ),

  FOREIGN KEY (component_id)
    REFERENCES whatsapp_template_components(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;


-- 7. whatsapp_template_carousel_card_components
CREATE TABLE whatsapp_template_carousel_card_components (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  card_id BIGINT UNSIGNED NOT NULL,

  component_type ENUM('HEADER','BODY','BUTTONS') NOT NULL,
  format ENUM('IMAGE','VIDEO','DOCUMENT') NULL,

  text TEXT NULL,
  media_handle VARCHAR(2048) NULL,  
  media_url VARCHAR(500) NULL,

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  FOREIGN KEY (card_id)
    REFERENCES whatsapp_template_carousel_cards(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;


-- 8. whatsapp_template_carousel_buttons
CREATE TABLE whatsapp_template_carousel_buttons (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  card_component_id BIGINT UNSIGNED NOT NULL,

  button_type ENUM('URL','QUICK_REPLY','PHONE_NUMBER') NOT NULL,
  text VARCHAR(150) NOT NULL,
  url VARCHAR(500) NULL,
  phone_number VARCHAR(30) NULL,

  button_index INT NOT NULL,

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uk_card_button (
    card_component_id, button_index
  ),

  FOREIGN KEY (card_component_id)
    REFERENCES whatsapp_template_carousel_card_components(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

-- 9. 

CREATE TABLE whatsapp_template_carousel_examples (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  carousel_component_id BIGINT UNSIGNED NOT NULL,

  header_text JSON NULL COMMENT 'List<String> for header text variables',
  header_handle JSON NULL COMMENT 'List<String> for header media handles',
  body_text JSON NULL COMMENT 'List<List<String>> for body text variables',

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  FOREIGN KEY (carousel_component_id)
    REFERENCES whatsapp_template_carousel_card_components(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;


-- whatsapp template varaibles table for storing variable metadata, sample values, and defaults for each component
CREATE TABLE whatsapp_template_variables (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  template_id    BIGINT UNSIGNED NOT NULL,

  -- Where this variable sits
  component_type ENUM('HEADER','BODY','BUTTON') NOT NULL,

  -- {{1}}, {{2}} etc — positional index within the component
  variable_index INT NOT NULL,

  -- Human label for UI: "customer_name", "order_id", "product_image"
  label VARCHAR(100) NULL,

  -- For BUTTON vars: which button (0-based), -1 if not a button var
  button_index INT NOT NULL DEFAULT -1,

  -- For CAROUSEL vars: which card (0-based), -1 for normal templates
  card_index INT NOT NULL DEFAULT -1,

  -- Fallback if value is empty at send time
  label_value VARCHAR(500) NULL,

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uk_var (
    template_id, component_type, variable_index,
    button_index, card_index
  ),

  FOREIGN KEY (template_id)
    REFERENCES whatsapp_templates(id) ON DELETE CASCADE

) ENGINE=InnoDB;


-- MEDIA UPLOAD (RESUMABLE)
-- 9. whatsapp_template_media_uploads
CREATE TABLE whatsapp_template_media_uploads (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  organization_id BIGINT UNSIGNED NOT NULL,
  project_id BIGINT UNSIGNED NOT NULL,
  waba_id VARCHAR(255) NOT NULL,

  session_id VARCHAR(255) NOT NULL,
  media_handle VARCHAR(2048) NULL,

  file_name VARCHAR(255) NOT NULL,
  file_size BIGINT NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  media_type ENUM('IMAGE','VIDEO','DOCUMENT','AUDIO') NOT NULL,

  status ENUM('PENDING','COMPLETED','FAILED') DEFAULT 'PENDING',

  is_chunked_upload TINYINT(1) DEFAULT 0,
  file_offset BIGINT DEFAULT 0,

  upload_response JSON NULL,

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,

  INDEX idx_session (session_id),
  INDEX idx_project (project_id)

) ENGINE=InnoDB;