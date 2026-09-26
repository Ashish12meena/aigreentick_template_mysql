-- =====================================================================
-- template_reset.sql
-- Drops every table owned by the template service, in FK-safe order.
--
-- Table list derived from the JPA entities in
--   com.aigreentick.services.template.domain.model
-- (12 entities, 12 tables) plus infrastructure.idempotency.IdempotencyRecord
-- (api_idempotency_keys). Order is leaf -> root:
--
--   button_supported_apps -> buttons -> components
--   carousel_buttons -> carousel_card_components -> carousel_cards -> components
--   carousel_examples -> components
--   examples -> components
--   components -> templates
--   variables -> templates
--   media_uploads (standalone, no FK)
--   system_templates (standalone, no FK)
--   api_idempotency_keys (standalone, no FK)
--
-- FOREIGN_KEY_CHECKS is toggled off as a belt-and-braces measure so the
-- script still succeeds if Hibernate named a constraint differently than
-- expected, or if a table is already missing.
--
-- DESTRUCTIVE. Run only against a dev/test schema.
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- Leaves (depend on buttons / carousel cards)
DROP TABLE IF EXISTS whatsapp_template_button_supported_apps;
DROP TABLE IF EXISTS whatsapp_template_carousel_buttons;
DROP TABLE IF EXISTS whatsapp_template_carousel_card_components;

-- Depend on components
DROP TABLE IF EXISTS whatsapp_template_buttons;
DROP TABLE IF EXISTS whatsapp_template_carousel_cards;
DROP TABLE IF EXISTS whatsapp_template_carousel_examples;
DROP TABLE IF EXISTS whatsapp_template_examples;

-- Depend on templates
DROP TABLE IF EXISTS whatsapp_template_components;
DROP TABLE IF EXISTS whatsapp_template_variables;

-- Root
DROP TABLE IF EXISTS whatsapp_templates;

-- Standalone (no FK to any of the above)
DROP TABLE IF EXISTS whatsapp_template_media_uploads;
DROP TABLE IF EXISTS system_templates;
DROP TABLE IF EXISTS api_idempotency_keys;

SET FOREIGN_KEY_CHECKS = 1;

-- Confirm nothing is left behind
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND (table_name LIKE 'whatsapp_template%' OR table_name IN ('api_idempotency_keys', 'system_templates'));