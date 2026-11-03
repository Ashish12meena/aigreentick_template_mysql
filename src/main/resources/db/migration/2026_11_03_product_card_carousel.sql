-- ----------------------------------------------------------------------------
-- Migration for an EXISTING apargo_wa_template schema (fresh installs get the
-- same result from template.sql). Run once, before deploying the build that
-- accepts product card carousels: without it, saving one fails with
-- "Data truncated for column 'format'" (or 'button_type').
--
-- What it does
--   * Card header format gains PRODUCT: the card shows a catalog product
--     chosen when the message is sent, instead of uploaded media.
--   * Card button type gains SPM: the product card's "View" button.
--
-- Safe to run on existing data: values are only added, none removed or
-- reordered, so every stored row keeps its value. Re-running is harmless.
-- ----------------------------------------------------------------------------

-- Run in the database the template service uses (its DB_NAME). On the dev server
-- platform.env sets DB_NAME=apargo_wa_messaging for every service, so there is no
-- separate apargo_wa_template database. Applied on dev 2026-11-03.

ALTER TABLE whatsapp_template_carousel_card_components
  MODIFY COLUMN format ENUM('IMAGE','VIDEO','DOCUMENT','PRODUCT') NULL;

ALTER TABLE whatsapp_template_carousel_buttons
  MODIFY COLUMN button_type ENUM('URL','QUICK_REPLY','PHONE_NUMBER','SPM') NOT NULL;
