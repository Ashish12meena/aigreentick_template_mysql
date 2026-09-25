drop schema apargo_wa_template;

create schema apargo_wa_template;

use apargo_wa_template;
-- WHATSAPP TEMPLATE SERVICE — ALL TABLES (MYSQL)
-- Timestamps: every *_at column is DATETIME(6) holding UTC. Values are set
-- by the application (java.time.Instant via @PrePersist / @PreUpdate), so
-- there are deliberately no DEFAULT / ON UPDATE clauses here.
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

  status ENUM('DRAFT','NEW_CREATED','SUBMITTED','PENDING','APPROVED','REJECTED','PAUSED','DISABLED','FAILED','UNKNOWN') DEFAULT 'PENDING',
  rejection_reason TEXT NULL,
  previous_category ENUM('MARKETING','UTILITY','AUTHENTICATION') NULL,

  meta_template_id VARCHAR(150) NULL,
  meta_status_raw VARCHAR(64) NULL,

  quality_rating ENUM('GREEN','YELLOW','RED','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',

  submission_payload JSON NULL,
  meta_response JSON NULL,

  created_by BIGINT UNSIGNED NULL,

  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  deleted_at DATETIME(6) NULL,

  -- Computed, never written by the application. 1 = live (exists or may exist
  -- on Meta), NULL = DRAFT / FAILED / soft-deleted. Must match the
  -- columnDefinition of WhatsappTemplate.liveFlag.
  live_flag TINYINT GENERATED ALWAYS AS (
    CASE WHEN deleted_at IS NULL AND status NOT IN ('DRAFT','FAILED') THEN 1 ELSE NULL END
  ) STORED,

  -- One LIVE template per (waba, name, language). NULLs never collide, so
  -- DRAFT / FAILED / deleted rows do not block reusing a name.
  UNIQUE KEY uk_waba_template_live (
    waba_id, name, language, live_flag
  ),

  INDEX idx_project_status (project_id, status),
  INDEX idx_waba_id (waba_id),
  INDEX idx_status_updated (status, updated_at)
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

  created_at DATETIME(6) NOT NULL,

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

  created_at DATETIME(6) NOT NULL,

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

  created_at DATETIME(6) NOT NULL,

  FOREIGN KEY (component_id)
    REFERENCES whatsapp_template_components(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;
-- 6. whatsapp_template_carousel_cards

CREATE TABLE whatsapp_template_carousel_cards (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  component_id BIGINT UNSIGNED NOT NULL,
  card_index INT NOT NULL,

  created_at DATETIME(6) NOT NULL,

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

  created_at DATETIME(6) NOT NULL,

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

  created_at DATETIME(6) NOT NULL,

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

  created_at DATETIME(6) NOT NULL,

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
  label_value VARCHAR(255) NULL,

  created_at DATETIME(6) NULL,

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

  created_at DATETIME(6) NOT NULL,
  completed_at DATETIME(6) NULL,

  INDEX idx_session (session_id),
  INDEX idx_project (project_id)

) ENGINE=InnoDB;

-- API INFRASTRUCTURE
-- 10. api_idempotency_keys
-- X-Idempotency-Key handling for create/send endpoints (API Standard §1).
-- Entity: infrastructure.idempotency.IdempotencyRecord. Rows expire after
-- idempotency.ttl (default 24h) and are purged hourly.
CREATE TABLE api_idempotency_keys (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  organization_id BIGINT UNSIGNED NOT NULL,
  project_id BIGINT UNSIGNED NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,

  request_fingerprint VARCHAR(64) NOT NULL,
  state VARCHAR(16) NOT NULL,

  response_status INT NULL,
  response_body LONGTEXT NULL,

  created_at DATETIME(6) NOT NULL,
  completed_at DATETIME(6) NULL,

  UNIQUE KEY uk_idempotency_scope_key (organization_id, project_id, idempotency_key),
  INDEX idx_idempotency_created_at (created_at)

) ENGINE=InnoDB;
