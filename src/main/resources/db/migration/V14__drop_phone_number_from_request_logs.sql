-- Remove the WhatsApp channel remnant from the audit log.
--
-- The phone number was only ever populated by the WhatsApp webhook, which is out of
-- the project scope (web chat only). Dropping it also aligns the audit log with the
-- data-minimisation principle (LGPD): no personal identifier is stored beyond what the
-- authenticated session already provides.

DROP INDEX IF EXISTS idx_request_logs_phone_number;

ALTER TABLE request_logs DROP COLUMN IF EXISTS phone_number;
