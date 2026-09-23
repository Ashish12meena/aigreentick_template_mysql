-- =====================================================================
-- template_seed.sql
-- Seeds one template per project for projects 1, 2, 3 and 257.
--
-- Content is the "test_ashwin16" template:
--   MARKETING (previously UTILITY), en, single BODY component,
--   two variables: {{1}} = name, {{2}} = company.
--
-- Names must be unique on (waba_id, name, language) -- uk_waba_template --
-- and all four projects share one WABA, so each gets its own name:
--   project   1 -> welcome_note_alpha
--   project   2 -> welcome_note_bravo
--   project   3 -> welcome_note_charlie
--   project 257 -> welcome_note_delta      <- assumed name
--
-- Built against the JPA entities (source of truth), so created_at /
-- updated_at are supplied explicitly: they are @PrePersist-populated in
-- Java (UTC Instant) and have NO database default.
--
-- Run template_reset.sql + let Hibernate re-create the schema first if you
-- want a clean slate.
-- =====================================================================

DROP PROCEDURE IF EXISTS seed_welcome_template;

DELIMITER $$

CREATE PROCEDURE seed_welcome_template(
    IN p_project_id      BIGINT,
    IN p_name            VARCHAR(150),
    IN p_meta_template_id VARCHAR(150)
)
BEGIN
    DECLARE v_now        DATETIME(6);
    DECLARE v_tpl_id     BIGINT;
    DECLARE v_body_id    BIGINT;

    SET v_now = UTC_TIMESTAMP(6);  -- all timestamps are stored in UTC

    INSERT INTO whatsapp_templates
      (organization_id, project_id, waba_id, name, category, language,
       status, quality_rating, rejection_reason, previous_category,
       meta_template_id, meta_status_raw, submission_payload, meta_response,
       created_by, created_at, updated_at, deleted_at)
    VALUES
      (1, p_project_id, '1436853954305849', p_name, 'MARKETING', 'en',
       'APPROVED', 'UNKNOWN', NULL, 'UTILITY',
       p_meta_template_id, NULL, NULL, NULL,
       NULL, v_now, v_now, NULL);

    SET v_tpl_id = LAST_INSERT_ID();

    -- BODY (component_order 0 -- this template has no header)
    INSERT INTO whatsapp_template_components
      (template_id, component_type, format, text, media_handle, media_url,
       add_security_recommendation, code_expiration_minutes, component_order, created_at)
    VALUES
      (v_tpl_id, 'BODY', NULL,
       'Hello {{1}}, welcome to {{2}}, you will enjoy here.',
       NULL, NULL, 0, NULL, 0, v_now);

    SET v_body_id = LAST_INSERT_ID();

    INSERT INTO whatsapp_template_examples
      (component_id, header_text, header_handle, body_text, created_at)
    VALUES
      (v_body_id, NULL, NULL,
       JSON_ARRAY(JSON_ARRAY('name', 'company')), v_now);

    INSERT INTO whatsapp_template_variables
      (template_id, component_type, variable_index, label, label_value,
       button_index, card_index, created_at)
    VALUES
      (v_tpl_id, 'BODY', 1, 'name',    'name',    -1, -1, v_now),
      (v_tpl_id, 'BODY', 2, 'company', 'company', -1, -1, v_now);
END$$

DELIMITER ;


START TRANSACTION;

CALL seed_welcome_template(  1, 'welcome_note_alpha',   '631284416741851');
CALL seed_welcome_template(  2, 'welcome_note_bravo',   '631284416741852');
CALL seed_welcome_template(  3, 'welcome_note_charlie', '631284416741853');
CALL seed_welcome_template(257, 'welcome_note_delta',   '631284416741854');

COMMIT;

DROP PROCEDURE IF EXISTS seed_welcome_template;


-- =====================================================================
-- Verify
-- =====================================================================
SELECT t.id, t.project_id, t.name, t.category, t.previous_category,
       t.status, t.meta_template_id, t.created_at,
       c.id AS component_id, c.component_type, c.component_order,
       e.id AS example_id, e.body_text
FROM whatsapp_templates t
JOIN whatsapp_template_components c ON c.template_id = t.id
LEFT JOIN whatsapp_template_examples e ON e.component_id = c.id
WHERE t.project_id IN (1, 2, 3, 257)
  AND t.deleted_at IS NULL
ORDER BY t.project_id, c.component_order;

SELECT template_id, variable_index, label, label_value, button_index, card_index
FROM whatsapp_template_variables
WHERE template_id IN (
    SELECT id FROM whatsapp_templates
    WHERE project_id IN (1, 2, 3, 257) AND deleted_at IS NULL
)
ORDER BY template_id, variable_index;