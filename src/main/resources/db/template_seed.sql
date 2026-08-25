-- =====================================================================
-- SEED DATA — apargo_wa_template (comprehensive, 24 templates)
-- Run AFTER V1__initial_schema.sql
-- Insert order is FK-safe: parents always inserted before children.
-- =====================================================================

USE apargo_wa_template;

START TRANSACTION;

-- ---------------------------------------------------------------------
-- 1. whatsapp_templates (root)
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_templates
  (id, organization_id, project_id, waba_id, name, category, language, status, rejection_reason, previous_category, meta_template_id, quality_rating, submission_payload, meta_response, created_by)
VALUES
  (1, 100, 10, '109876543210001', 'order_confirmation', 'UTILITY', 'en_US', 'APPROVED', NULL, NULL, '987654321000123', 'GREEN', NULL, NULL, 1),
  (2, 100, 10, '109876543210001', 'otp_login', 'AUTHENTICATION', 'en', 'APPROVED', NULL, NULL, '987654321000124', 'GREEN', NULL, NULL, 1),
  (3, 100, 10, '109876543210001', 'otp_one_tap', 'AUTHENTICATION', 'en', 'APPROVED', NULL, NULL, '987654321000125', 'GREEN', NULL, NULL, 1),
  (4, 100, 10, '109876543210001', 'otp_zero_tap', 'AUTHENTICATION', 'en', 'PENDING', NULL, NULL, NULL, 'UNKNOWN', NULL, NULL, 1),
  (5, 100, 10, '109876543210001', 'festive_sale_carousel', 'MARKETING', 'en_US', 'PENDING', NULL, NULL, NULL, 'UNKNOWN', NULL, NULL, 1),
  (6, 101, 11, '109876543210002', 'welcome_message', 'UTILITY', 'hi', 'DRAFT', NULL, NULL, NULL, 'UNKNOWN', NULL, NULL, 2),
  (7, 101, 11, '109876543210002', 'payment_reminder', 'UTILITY', 'en_US', 'REJECTED', 'Template contains promotional language not allowed for UTILITY category', 'MARKETING', NULL, 'UNKNOWN', NULL, NULL, 2),
  (8, 101, 11, '109876543210002', 'shipping_update_video', 'UTILITY', 'en_US', 'APPROVED', NULL, NULL, '987654321000126', 'YELLOW', NULL, NULL, 2),
  (9, 101, 11, '109876543210002', 'document_invoice', 'UTILITY', 'en_US', 'APPROVED', NULL, NULL, '987654321000127', 'GREEN', NULL, NULL, 2),
  (10, 100, 10, '109876543210001', 'store_location', 'MARKETING', 'en_US', 'APPROVED', NULL, NULL, '987654321000128', 'GREEN', NULL, NULL, 1),
  (11, 100, 10, '109876543210001', 'product_launch', 'MARKETING', 'en_US', 'APPROVED', NULL, NULL, '987654321000129', 'RED', NULL, NULL, 1),
  (12, 100, 10, '109876543210001', 'flash_sale_lto', 'MARKETING', 'en_US', 'APPROVED', NULL, NULL, '987654321000130', 'GREEN', NULL, NULL, 1),
  (13, 100, 10, '109876543210001', 'multi_product_catalog', 'MARKETING', 'en_US', 'SUBMITTED', NULL, NULL, NULL, 'UNKNOWN', NULL, NULL, 1),
  (14, 100, 10, '109876543210001', 'single_product_msg', 'MARKETING', 'en_US', 'SUBMITTED', NULL, NULL, NULL, 'UNKNOWN', NULL, NULL, 1),
  (15, 101, 11, '109876543210002', 'quick_survey', 'UTILITY', 'en_US', 'APPROVED', NULL, NULL, '987654321000131', 'YELLOW', NULL, NULL, 2),
  (16, 101, 11, '109876543210002', 'call_support', 'UTILITY', 'en_US', 'APPROVED', NULL, NULL, '987654321000132', 'GREEN', NULL, NULL, 2),
  (17, 101, 11, '109876543210002', 'account_paused_notice', 'UTILITY', 'en_US', 'PAUSED', NULL, NULL, '987654321000133', 'RED', NULL, NULL, 2),
  (18, 101, 11, '109876543210002', 'account_disabled_notice', 'UTILITY', 'en_US', 'DISABLED', NULL, NULL, '987654321000134', 'RED', NULL, NULL, 2),
  (19, 100, 10, '109876543210001', 'sync_failed_template', 'MARKETING', 'en_US', 'FAILED', NULL, NULL, NULL, 'UNKNOWN', NULL, NULL, 1),
  (20, 100, 10, '109876543210001', 'new_created_draft', 'UTILITY', 'en_US', 'NEW_CREATED', NULL, NULL, NULL, 'UNKNOWN', NULL, NULL, 1),
  (21, 100, 10, '109876543210001', 'multi_format_carousel', 'MARKETING', 'en_US', 'APPROVED', NULL, NULL, '987654321000135', 'GREEN', NULL, NULL, 1),
  (22, 101, 11, '109876543210002', 'minimal_body_only', 'UTILITY', 'en_US', 'APPROVED', NULL, NULL, '987654321000136', 'GREEN', NULL, NULL, 2),
  (23, 100, 10, '109876543210001', 'image_promo', 'MARKETING', 'en_US', 'APPROVED', NULL, NULL, '987654321000137', 'YELLOW', NULL, NULL, 1),
  (24, 100, 10, '109876543210001', 'coupon_code_promo', 'MARKETING', 'en_US', 'APPROVED', NULL, NULL, '987654321000138', 'GREEN', NULL, NULL, 1);

-- ---------------------------------------------------------------------
-- 2. whatsapp_template_components (depends on whatsapp_templates)
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_template_components
  (id, template_id, component_type, format, text, media_handle, media_url, add_security_recommendation, code_expiration_minutes, component_order)
VALUES
  (1, 1, 'HEADER', 'TEXT', 'Order Confirmed! 🎉', NULL, NULL, 0, NULL, 1),
  (2, 1, 'BODY', NULL, 'Hi {{1}}, your order #{{2}} has been confirmed and will arrive by {{3}}.', NULL, NULL, 0, NULL, 2),
  (3, 1, 'FOOTER', NULL, 'Thank you for shopping with us', NULL, NULL, 0, NULL, 3),
  (4, 1, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 4),
  (5, 2, 'BODY', NULL, '{{1}} is your verification code. For your security, do not share this code.', NULL, NULL, 1, 10, 1),
  (6, 2, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 2),
  (7, 3, 'BODY', NULL, '{{1}} is your verification code.', NULL, NULL, 1, 5, 1),
  (8, 3, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 2),
  (9, 4, 'BODY', NULL, '{{1}} is your verification code.', NULL, NULL, 1, 3, 1),
  (10, 4, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 2),
  (11, 5, 'BODY', NULL, 'Check out our festive sale collection, {{1}}!', NULL, NULL, 0, NULL, 1),
  (12, 5, 'CAROUSEL', NULL, NULL, NULL, NULL, 0, NULL, 2),
  (13, 6, 'BODY', NULL, 'Namaste {{1}}, Apargo mein aapka swagat hai!', NULL, NULL, 0, NULL, 1),
  (14, 7, 'BODY', NULL, 'Hi {{1}}, your payment of {{2}} is due on {{3}}.', NULL, NULL, 0, NULL, 1),
  (15, 8, 'HEADER', 'VIDEO', NULL, '4:shipvideo:video/mp4:ARZvid1', NULL, 0, NULL, 1),
  (16, 8, 'BODY', NULL, 'Hi {{1}}, your shipment #{{2}} is on its way!', NULL, NULL, 0, NULL, 2),
  (17, 8, 'FOOTER', NULL, 'Track anytime from the app', NULL, NULL, 0, NULL, 3),
  (18, 9, 'HEADER', 'DOCUMENT', NULL, '4:invoicedoc:application/pdf:ARZdoc1', NULL, 0, NULL, 1),
  (19, 9, 'BODY', NULL, 'Hi {{1}}, please find your invoice attached.', NULL, NULL, 0, NULL, 2),
  (20, 9, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 3),
  (21, 10, 'HEADER', 'LOCATION', NULL, NULL, NULL, 0, NULL, 1),
  (22, 10, 'BODY', NULL, 'Visit our nearest store for exclusive in-store offers!', NULL, NULL, 0, NULL, 2),
  (23, 10, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 3),
  (24, 11, 'HEADER', 'PRODUCT', NULL, NULL, NULL, 0, NULL, 1),
  (25, 11, 'BODY', NULL, 'Our new product is finally here, {{1}}! Check it out.', NULL, NULL, 0, NULL, 2),
  (26, 11, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 3),
  (27, 12, 'HEADER', 'TEXT', '⚡ Flash Sale Alert', NULL, NULL, 0, NULL, 1),
  (28, 12, 'BODY', NULL, '{{1}}, get flat 50% off for the next 24 hours only!', NULL, NULL, 0, NULL, 2),
  (29, 12, 'LIMITED_TIME_OFFER', NULL, NULL, NULL, NULL, 0, NULL, 3),
  (30, 12, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 4),
  (31, 13, 'BODY', NULL, 'Browse our top picks this week, {{1}}.', NULL, NULL, 0, NULL, 1),
  (32, 13, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 2),
  (33, 14, 'BODY', NULL, 'Check out this product just for you, {{1}}.', NULL, NULL, 0, NULL, 1),
  (34, 14, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 2),
  (35, 15, 'BODY', NULL, 'How was your experience with us, {{1}}?', NULL, NULL, 0, NULL, 1),
  (36, 15, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 2),
  (37, 16, 'BODY', NULL, 'Need help? Reach out to our support team anytime.', NULL, NULL, 0, NULL, 1),
  (38, 16, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 2),
  (39, 17, 'BODY', NULL, 'Hi {{1}}, we noticed unusual activity and paused notifications temporarily.', NULL, NULL, 0, NULL, 1),
  (40, 17, 'FOOTER', NULL, 'Contact support if this seems wrong', NULL, NULL, 0, NULL, 2),
  (41, 18, 'BODY', NULL, 'This template has been disabled due to repeated policy violations.', NULL, NULL, 0, NULL, 1),
  (42, 19, 'BODY', NULL, 'This template failed to sync from Meta due to a validation error.', NULL, NULL, 0, NULL, 1),
  (43, 20, 'BODY', NULL, 'Hi {{1}}, this is a newly created template awaiting content.', NULL, NULL, 0, NULL, 1),
  (44, 21, 'BODY', NULL, 'Explore our best sellers, {{1}}!', NULL, NULL, 0, NULL, 1),
  (45, 21, 'CAROUSEL', NULL, NULL, NULL, NULL, 0, NULL, 2),
  (46, 22, 'BODY', NULL, 'Your request has been received.', NULL, NULL, 0, NULL, 1),
  (47, 23, 'HEADER', 'IMAGE', NULL, '4:promoimg:image/jpeg:ARZp1', NULL, 0, NULL, 1),
  (48, 23, 'BODY', NULL, 'New season, new styles, {{1}}!', NULL, NULL, 0, NULL, 2),
  (49, 23, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 3),
  (50, 24, 'BODY', NULL, 'Use this code for 20% off your next order, {{1}}!', NULL, NULL, 0, NULL, 1),
  (51, 24, 'BUTTONS', NULL, NULL, NULL, NULL, 0, NULL, 2);

-- ---------------------------------------------------------------------
-- 3. whatsapp_template_buttons (depends on whatsapp_template_components)
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_template_buttons
  (id, component_id, button_type, text, url, phone_number, otp_type, button_index, example)
VALUES
  (1, 4, 'QUICK_REPLY', 'Track Order', NULL, NULL, NULL, 0, NULL),
  (2, 4, 'URL', 'View Invoice', 'https://apargo.example.com/orders/{{1}}', NULL, NULL, 1, JSON_ARRAY('12345')),
  (3, 6, 'OTP', 'Copy Code', NULL, NULL, 'COPY_CODE', 0, NULL),
  (4, 8, 'OTP', 'Continue', NULL, NULL, 'ONE_TAP', 0, NULL),
  (5, 10, 'OTP', 'Auto-fill', NULL, NULL, 'ZERO_TAP', 0, NULL),
  (6, 20, 'URL', 'Pay Now', 'https://apargo.example.com/pay/{{1}}', NULL, NULL, 0, JSON_ARRAY('INV998')),
  (7, 23, 'PHONE_NUMBER', 'Call Store', NULL, '+911234567890', NULL, 0, NULL),
  (8, 26, 'CATALOG', 'View Catalog', NULL, NULL, NULL, 0, NULL),
  (9, 30, 'URL', 'Shop Flash Sale', 'https://apargo.example.com/flash-sale', NULL, NULL, 0, NULL),
  (10, 32, 'MPM', 'View Products', NULL, NULL, NULL, 0, NULL),
  (11, 34, 'SPM', 'View Product', NULL, NULL, NULL, 0, NULL),
  (12, 36, 'QUICK_REPLY', 'Great', NULL, NULL, NULL, 0, NULL),
  (13, 36, 'QUICK_REPLY', 'Okay', NULL, NULL, NULL, 1, NULL),
  (14, 36, 'QUICK_REPLY', 'Poor', NULL, NULL, NULL, 2, NULL),
  (15, 38, 'PHONE_NUMBER', 'Call Support', NULL, '+911800123456', NULL, 0, NULL),
  (16, 49, 'URL', 'Browse Collection', 'https://apargo.example.com/new-season', NULL, NULL, 0, NULL),
  (17, 51, 'COPY_CODE', 'Copy Coupon', NULL, NULL, NULL, 0, JSON_ARRAY('SAVE20'));

-- ---------------------------------------------------------------------
-- 4. whatsapp_template_button_supported_apps (depends on whatsapp_template_buttons)
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_template_button_supported_apps
  (id, button_id, package_name, signature_hash)
VALUES
  (1, 3, 'com.apargo.android', 'ABCDEF1234567890abcdef1234567890ABCDEF12'),
  (2, 4, 'com.apargo.android', 'FEDCBA0987654321fedcba0987654321FEDCBA09');

-- ---------------------------------------------------------------------
-- 5. whatsapp_template_examples (depends on whatsapp_template_components)
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_template_examples
  (id, component_id, header_text, header_handle, body_text)
VALUES
  (1, 2, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh','ORD1234','24 July'))),
  (2, 5, NULL, NULL, JSON_ARRAY(JSON_ARRAY('482913'))),
  (3, 7, NULL, NULL, JSON_ARRAY(JSON_ARRAY('193847'))),
  (4, 9, NULL, NULL, JSON_ARRAY(JSON_ARRAY('556677'))),
  (5, 11, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh'))),
  (6, 15, NULL, JSON_ARRAY('4:shipvideo:video/mp4:ARZvid1'), NULL),
  (7, 16, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh','SHIP555'))),
  (8, 18, NULL, JSON_ARRAY('4:invoicedoc:application/pdf:ARZdoc1'), NULL),
  (9, 19, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh'))),
  (10, 25, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh'))),
  (11, 28, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh'))),
  (12, 31, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh'))),
  (13, 33, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh'))),
  (14, 35, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh'))),
  (15, 39, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh'))),
  (16, 44, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh'))),
  (17, 47, NULL, JSON_ARRAY('4:promoimg:image/jpeg:ARZp1'), NULL),
  (18, 48, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh'))),
  (19, 50, NULL, NULL, JSON_ARRAY(JSON_ARRAY('Vidyansh')));

-- ---------------------------------------------------------------------
-- 6. whatsapp_template_carousel_cards (depends on whatsapp_template_components)
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_template_carousel_cards
  (id, component_id, card_index)
VALUES
  (1, 12, 0),
  (2, 12, 1),
  (3, 45, 0),
  (4, 45, 1),
  (5, 45, 2);

-- ---------------------------------------------------------------------
-- 7. whatsapp_template_carousel_card_components (depends on whatsapp_template_carousel_cards)
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_template_carousel_card_components
  (id, card_id, component_type, format, text, media_handle, media_url)
VALUES
  (1, 1, 'HEADER', 'IMAGE', NULL, '4:card1header:image/jpeg:ARZxx1', NULL),
  (2, 1, 'BODY', NULL, 'Flat 40% off on Diwali collection', NULL, NULL),
  (3, 1, 'BUTTONS', NULL, NULL, NULL, NULL),
  (4, 2, 'HEADER', 'IMAGE', NULL, '4:card2header:image/jpeg:ARZxx2', NULL),
  (5, 2, 'BODY', NULL, 'Buy 1 Get 1 on festive wear', NULL, NULL),
  (6, 2, 'BUTTONS', NULL, NULL, NULL, NULL),
  (7, 3, 'HEADER', 'VIDEO', NULL, '4:cc1:video/mp4:ARZv1', NULL),
  (8, 3, 'BODY', NULL, 'Watch the unboxing of our best seller', NULL, NULL),
  (9, 3, 'BUTTONS', NULL, NULL, NULL, NULL),
  (10, 4, 'HEADER', 'DOCUMENT', NULL, '4:cc2:application/pdf:ARZd1', NULL),
  (11, 4, 'BODY', NULL, 'Download the full spec sheet', NULL, NULL),
  (12, 4, 'BUTTONS', NULL, NULL, NULL, NULL),
  (13, 5, 'HEADER', 'IMAGE', NULL, '4:cc3:image/jpeg:ARZi1', NULL),
  (14, 5, 'BODY', NULL, 'See it in every color', NULL, NULL),
  (15, 5, 'BUTTONS', NULL, NULL, NULL, NULL);

-- ---------------------------------------------------------------------
-- 8. whatsapp_template_carousel_buttons (depends on whatsapp_template_carousel_card_components)
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_template_carousel_buttons
  (id, card_component_id, button_type, text, url, phone_number, button_index)
VALUES
  (1, 3, 'URL', 'Shop Now', 'https://apargo.example.com/sale/diwali', NULL, 0),
  (2, 6, 'URL', 'Shop Now', 'https://apargo.example.com/sale/bogo', NULL, 0),
  (3, 9, 'QUICK_REPLY', 'I want one', NULL, NULL, 0),
  (4, 12, 'PHONE_NUMBER', 'Call Sales', NULL, '+911234500000', 0),
  (5, 15, 'URL', 'Shop Now', 'https://apargo.example.com/bestseller', NULL, 0);

-- ---------------------------------------------------------------------
-- 9. whatsapp_template_carousel_examples (depends on whatsapp_template_carousel_card_components)
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_template_carousel_examples
  (id, carousel_component_id, header_text, header_handle, body_text)
VALUES
  (1, 1, NULL, JSON_ARRAY('4:card1header:image/jpeg:ARZxx1'), NULL),
  (2, 4, NULL, JSON_ARRAY('4:card2header:image/jpeg:ARZxx2'), NULL),
  (3, 7, NULL, JSON_ARRAY('4:cc1:video/mp4:ARZv1'), NULL),
  (4, 10, NULL, JSON_ARRAY('4:cc2:application/pdf:ARZd1'), NULL),
  (5, 13, NULL, JSON_ARRAY('4:cc3:image/jpeg:ARZi1'), NULL);

-- ---------------------------------------------------------------------
-- 10. whatsapp_template_variables (depends on whatsapp_templates)
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_template_variables
  (id, template_id, component_type, variable_index, label, button_index, card_index, label_value)
VALUES
  (1, 1, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (2, 1, 'BODY', 2, 'order_id', -1, -1, 'N/A'),
  (3, 1, 'BODY', 3, 'delivery_date', -1, -1, 'soon'),
  (4, 1, 'BUTTON', 1, 'order_id_url', 1, -1, '0'),
  (5, 2, 'BODY', 1, 'otp_code', -1, -1, NULL),
  (6, 3, 'BODY', 1, 'otp_code', -1, -1, NULL),
  (7, 4, 'BODY', 1, 'otp_code', -1, -1, NULL),
  (8, 5, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (9, 6, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (10, 7, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (11, 7, 'BODY', 2, 'amount_due', -1, -1, '0'),
  (12, 7, 'BODY', 3, 'due_date', -1, -1, 'soon'),
  (13, 8, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (14, 8, 'BODY', 2, 'shipment_id', -1, -1, 'N/A'),
  (15, 9, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (16, 9, 'BUTTON', 1, 'invoice_id_url', 0, -1, '0'),
  (17, 11, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (18, 12, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (19, 13, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (20, 14, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (21, 15, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (22, 17, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (23, 20, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (24, 21, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (25, 23, 'BODY', 1, 'customer_name', -1, -1, 'Customer'),
  (26, 24, 'BODY', 1, 'customer_name', -1, -1, 'Customer');

-- ---------------------------------------------------------------------
-- 11. whatsapp_template_media_uploads (standalone — no FK dependency)
-- ---------------------------------------------------------------------
INSERT INTO whatsapp_template_media_uploads
  (id, organization_id, project_id, waba_id, session_id, media_handle, file_name, file_size, mime_type, media_type, status, is_chunked_upload, file_offset, upload_response, completed_at)
VALUES
  (1, 100, 10, '109876543210001', 'sess-abc123', '4:xyz:image/jpeg:ARZgen1', 'diwali_banner.jpg', 245678, 'image/jpeg', 'IMAGE', 'COMPLETED', 0, 245678, NULL, CURRENT_TIMESTAMP),
  (2, 101, 11, '109876543210002', 'sess-def456', NULL, 'welcome_video.mp4', 5242880, 'video/mp4', 'VIDEO', 'PENDING', 1, 1048576, NULL, NULL),
  (3, 100, 10, '109876543210001', 'sess-ghi789', '4:doc1:application/pdf:ARZgen2', 'product_spec.pdf', 98234, 'application/pdf', 'DOCUMENT', 'COMPLETED', 0, 98234, NULL, CURRENT_TIMESTAMP),
  (4, 101, 11, '109876543210002', 'sess-jkl012', NULL, 'voice_note.ogg', 54321, 'audio/ogg', 'AUDIO', 'FAILED', 0, 0, NULL, NULL),
  (5, 100, 10, '109876543210001', 'sess-mno345', '4:img2:image/jpeg:ARZgen3', 'product_launch.jpg', 187650, 'image/jpeg', 'IMAGE', 'COMPLETED', 0, 187650, NULL, CURRENT_TIMESTAMP),
  (6, 101, 11, '109876543210002', 'sess-pqr678', NULL, 'catalog_video.mp4', 8388608, 'video/mp4', 'VIDEO', 'PENDING', 1, 2097152, NULL, NULL);

COMMIT;

-- =====================================================================
-- Sanity check — row counts per table
-- =====================================================================
-- SELECT 'whatsapp_templates' AS table_name, COUNT(*) as rows  FROM whatsapp_templates
-- UNION ALL SELECT 'whatsapp_template_components', COUNT(*) FROM whatsapp_template_components
-- UNION ALL SELECT 'whatsapp_template_buttons', COUNT(*) FROM whatsapp_template_buttons
-- UNION ALL SELECT 'whatsapp_template_button_supported_apps', COUNT(*) FROM whatsapp_template_button_supported_apps
-- UNION ALL SELECT 'whatsapp_template_examples', COUNT(*) FROM whatsapp_template_examples
-- UNION ALL SELECT 'whatsapp_template_carousel_cards', COUNT(*) FROM whatsapp_template_carousel_cards
-- UNION ALL SELECT 'whatsapp_template_carousel_card_components', COUNT(*) FROM whatsapp_template_carousel_card_components
-- UNION ALL SELECT 'whatsapp_template_carousel_buttons', COUNT(*) FROM whatsapp_template_carousel_buttons
-- UNION ALL SELECT 'whatsapp_template_carousel_examples', COUNT(*) FROM whatsapp_template_carousel_examples
-- UNION ALL SELECT 'whatsapp_template_variables', COUNT(*) FROM whatsapp_template_variables
-- UNION ALL SELECT 'whatsapp_template_media_uploads', COUNT(*) FROM whatsapp_template_media_uploads;