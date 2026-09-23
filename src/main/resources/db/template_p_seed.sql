-- =====================================================================
-- template_seed.sql
-- Seeds one draft template for each of projects 1, 2, 3 and 257.
--
-- Mirrors this create payload exactly, nothing added:
--   draft: true, category MARKETING, language en,
--   one BODY component, addSecurityRecommendation false,
--   example bodyText [["name","company"]],
--   variables: {{1}} = name, {{2}} = company
--
-- draft: true  ->  status DRAFT, meta_template_id NULL (never sent to Meta).
-- No header, so no media_handle / media_url.
-- previous_category is NULL: it is not in the payload, and is only set by
-- the service when Meta re-categorises a template.
--
-- Names must be unique on (waba_id, name, language) -- uk_waba_template --
-- and all four share one WABA, so each project gets its own name:
--   project   1 -> welcome_note_alpha
--   project   2 -> welcome_note_bravo
--   project   3 -> welcome_note_charlie
--   project 257 -> welcome_note_delta
--
-- created_at / updated_at are supplied explicitly: they are
-- @PrePersist-populated in Java (UTC Instant) and have NO database default.
--
-- Plain SQL: no stored procedure, no DELIMITER, runs in any client.
-- =====================================================================

START TRANSACTION;

SET @now  = UTC_TIMESTAMP(6);  -- all timestamps are stored in UTC
SET @waba = '1436853954305849';
SET @org  = 1;
SET @text = 'Hello {{1}}, welcome to {{2}}, you will enjoy here.';


-- ---------------------------------------------------------------------
-- PROJECT 1  ->  welcome_note_alpha
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_templates
  (organization_id, project_id, waba_id, name, category, language,
   status, quality_rating, rejection_reason, previous_category,
   meta_template_id, meta_status_raw, submission_payload, meta_response,
   created_by, created_at, updated_at, deleted_at)
VALUES
  (@org, 1, @waba, 'welcome_note_alpha', 'MARKETING', 'en',
   'DRAFT', 'UNKNOWN', NULL, NULL,
   NULL, NULL, NULL, NULL,
   NULL, @now, @now, NULL);

SET @tpl_id = LAST_INSERT_ID();

INSERT INTO whatsapp_template_components
  (template_id, component_type, format, text,
   add_security_recommendation, code_expiration_minutes, component_order, created_at)
VALUES
  (@tpl_id, 'BODY', NULL, @text, 0, NULL, 0, @now);

SET @body_id = LAST_INSERT_ID();

INSERT INTO whatsapp_template_examples
  (component_id, header_text, header_handle, body_text, created_at)
VALUES
  (@body_id, NULL, NULL, JSON_ARRAY(JSON_ARRAY('name', 'company')), @now);

INSERT INTO whatsapp_template_variables
  (template_id, component_type, variable_index, label, label_value,
   button_index, card_index, created_at)
VALUES
  (@tpl_id, 'BODY', 1, 'name',    'name',    -1, -1, @now),
  (@tpl_id, 'BODY', 2, 'company', 'company', -1, -1, @now);


-- ---------------------------------------------------------------------
-- PROJECT 2  ->  welcome_note_bravo
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_templates
  (organization_id, project_id, waba_id, name, category, language,
   status, quality_rating, rejection_reason, previous_category,
   meta_template_id, meta_status_raw, submission_payload, meta_response,
   created_by, created_at, updated_at, deleted_at)
VALUES
  (@org, 2, @waba, 'welcome_note_bravo', 'MARKETING', 'en',
   'DRAFT', 'UNKNOWN', NULL, NULL,
   NULL, NULL, NULL, NULL,
   NULL, @now, @now, NULL);

SET @tpl_id = LAST_INSERT_ID();

INSERT INTO whatsapp_template_components
  (template_id, component_type, format, text,
   add_security_recommendation, code_expiration_minutes, component_order, created_at)
VALUES
  (@tpl_id, 'BODY', NULL, @text, 0, NULL, 0, @now);

SET @body_id = LAST_INSERT_ID();

INSERT INTO whatsapp_template_examples
  (component_id, header_text, header_handle, body_text, created_at)
VALUES
  (@body_id, NULL, NULL, JSON_ARRAY(JSON_ARRAY('name', 'company')), @now);

INSERT INTO whatsapp_template_variables
  (template_id, component_type, variable_index, label, label_value,
   button_index, card_index, created_at)
VALUES
  (@tpl_id, 'BODY', 1, 'name',    'name',    -1, -1, @now),
  (@tpl_id, 'BODY', 2, 'company', 'company', -1, -1, @now);


-- ---------------------------------------------------------------------
-- PROJECT 3  ->  welcome_note_charlie
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_templates
  (organization_id, project_id, waba_id, name, category, language,
   status, quality_rating, rejection_reason, previous_category,
   meta_template_id, meta_status_raw, submission_payload, meta_response,
   created_by, created_at, updated_at, deleted_at)
VALUES
  (@org, 3, @waba, 'welcome_note_charlie', 'MARKETING', 'en',
   'DRAFT', 'UNKNOWN', NULL, NULL,
   NULL, NULL, NULL, NULL,
   NULL, @now, @now, NULL);

SET @tpl_id = LAST_INSERT_ID();

INSERT INTO whatsapp_template_components
  (template_id, component_type, format, text,
   add_security_recommendation, code_expiration_minutes, component_order, created_at)
VALUES
  (@tpl_id, 'BODY', NULL, @text, 0, NULL, 0, @now);

SET @body_id = LAST_INSERT_ID();

INSERT INTO whatsapp_template_examples
  (component_id, header_text, header_handle, body_text, created_at)
VALUES
  (@body_id, NULL, NULL, JSON_ARRAY(JSON_ARRAY('name', 'company')), @now);

INSERT INTO whatsapp_template_variables
  (template_id, component_type, variable_index, label, label_value,
   button_index, card_index, created_at)
VALUES
  (@tpl_id, 'BODY', 1, 'name',    'name',    -1, -1, @now),
  (@tpl_id, 'BODY', 2, 'company', 'company', -1, -1, @now);


-- ---------------------------------------------------------------------
-- PROJECT 257  ->  welcome_note_delta
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_templates
  (organization_id, project_id, waba_id, name, category, language,
   status, quality_rating, rejection_reason, previous_category,
   meta_template_id, meta_status_raw, submission_payload, meta_response,
   created_by, created_at, updated_at, deleted_at)
VALUES
  (@org, 257, @waba, 'welcome_note_delta', 'MARKETING', 'en',
   'DRAFT', 'UNKNOWN', NULL, NULL,
   NULL, NULL, NULL, NULL,
   NULL, @now, @now, NULL);

SET @tpl_id = LAST_INSERT_ID();

INSERT INTO whatsapp_template_components
  (template_id, component_type, format, text,
   add_security_recommendation, code_expiration_minutes, component_order, created_at)
VALUES
  (@tpl_id, 'BODY', NULL, @text, 0, NULL, 0, @now);

SET @body_id = LAST_INSERT_ID();

INSERT INTO whatsapp_template_examples
  (component_id, header_text, header_handle, body_text, created_at)
VALUES
  (@body_id, NULL, NULL, JSON_ARRAY(JSON_ARRAY('name', 'company')), @now);

INSERT INTO whatsapp_template_variables
  (template_id, component_type, variable_index, label, label_value,
   button_index, card_index, created_at)
VALUES
  (@tpl_id, 'BODY', 1, 'name',    'name',    -1, -1, @now),
  (@tpl_id, 'BODY', 2, 'company', 'company', -1, -1, @now);

COMMIT;


-- =====================================================================
-- Verify
-- =====================================================================
SELECT t.id, t.project_id, t.name, t.category, t.status, t.created_at,
       c.id AS component_id, c.component_type, c.component_order,
       e.id AS example_id, e.body_text
FROM whatsapp_templates t
JOIN whatsapp_template_components c ON c.template_id = t.id
LEFT JOIN whatsapp_template_examples e ON e.component_id = c.id
WHERE t.project_id IN (1, 2, 3, 257)
  AND t.deleted_at IS NULL
ORDER BY t.project_id;

SELECT template_id, variable_index, label, label_value, button_index, card_index
FROM whatsapp_template_variables
WHERE template_id IN (
    SELECT id FROM whatsapp_templates
    WHERE project_id IN (1, 2, 3, 257) AND deleted_at IS NULL
)
ORDER BY template_id, variable_index;