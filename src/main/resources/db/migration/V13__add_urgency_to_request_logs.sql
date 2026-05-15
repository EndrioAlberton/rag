-- Add urgency classification to request_logs (BAIXA / MEDIA / ALTA)
ALTER TABLE request_logs
    ADD COLUMN IF NOT EXISTS urgency VARCHAR(10);

CREATE INDEX IF NOT EXISTS idx_request_logs_urgency ON request_logs(urgency);
