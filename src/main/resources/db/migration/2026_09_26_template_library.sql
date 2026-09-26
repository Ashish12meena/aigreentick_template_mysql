-- ----------------------------------------------------------------------------
-- TEMPLATE LIBRARY - system-level templates (no org / project / waba).
--
-- payload stores the template in the SAME format as the normal create
-- request (CreateTemplateRequestDto): { "template": {...}, "variables": [...] }
-- so it can be parsed with the existing DTOs and sent to the normal
-- create API without conversion.
--
-- name / language / category are copied from payload.template on save
-- (used for listing and filtering only).
--
-- Timestamps: DATETIME(6) UTC, set by the application (@PrePersist / @PreUpdate).
--
-- Run once on every existing database before deploying: prod uses
-- ddl-auto=validate and will not start without this table.
-- ----------------------------------------------------------------------------

USE apargo_wa_template;

-- If you already created system_templates from an earlier copy of this file,
-- run only this instead of the CREATE TABLE below:
--   ALTER TABLE system_templates
--     ADD COLUMN sample_media_url VARCHAR(500) NULL AFTER description;

CREATE TABLE system_templates (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

  name        VARCHAR(150) NOT NULL COMMENT 'Same as payload.template.name',
  language    VARCHAR(10)  NOT NULL COMMENT 'Same as payload.template.language',
  category    ENUM('MARKETING','UTILITY','AUTHENTICATION') NOT NULL COMMENT 'Same as payload.template.category',
  description VARCHAR(500) NULL     COMMENT 'Short text shown in the library list',
  sample_media_url VARCHAR(500) NULL COMMENT 'Preview-only header media URL on our storage; never sent to Meta',

  payload JSON NOT NULL COMMENT 'CreateTemplateRequestDto format: {template, variables}',

  is_active TINYINT(1) NOT NULL DEFAULT 1,

  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,

  UNIQUE KEY uk_system_template_name_lang (name, language),
  INDEX idx_system_template_category (is_active, category)
) ENGINE=InnoDB;
