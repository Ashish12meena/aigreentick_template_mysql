-- ----------------------------------------------------------------------------
-- Migration for an EXISTING apargo_wa_template schema (fresh installs get the
-- same result from template.sql). Run once, before deploying the new build:
-- prod uses ddl-auto=validate and will refuse to start without live_flag.
--
-- What it does
--   * Adds live_flag: 1 for live templates, NULL for DRAFT / FAILED / deleted.
--   * Replaces uk_waba_template (waba_id, name, language) - which blocked a
--     name forever once ANY row used it - with uk_waba_template_live, which
--     only constrains live rows.
--   * Adds idx_status_updated for the stuck-SUBMITTED reconciliation job.
--
-- Safe to run on existing data: the old key was stricter than the new one,
-- so no current rows can violate uk_waba_template_live.
-- ----------------------------------------------------------------------------

USE apargo_wa_template;

ALTER TABLE whatsapp_templates
  ADD COLUMN live_flag TINYINT GENERATED ALWAYS AS (
    CASE WHEN deleted_at IS NULL AND status NOT IN ('DRAFT','FAILED') THEN 1 ELSE NULL END
  ) STORED AFTER deleted_at;

-- Add the new key before dropping the old one so there is no window without
-- a uniqueness guarantee.
ALTER TABLE whatsapp_templates
  ADD UNIQUE KEY uk_waba_template_live (waba_id, name, language, live_flag);

ALTER TABLE whatsapp_templates
  DROP INDEX uk_waba_template;

ALTER TABLE whatsapp_templates
  ADD INDEX idx_status_updated (status, updated_at);

-- Verify
-- SHOW CREATE TABLE whatsapp_templates;
