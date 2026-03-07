-- Add conversation_id to request_logs for linking logs to specific conversations
ALTER TABLE request_logs ADD COLUMN IF NOT EXISTS conversation_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_request_logs_conversation_id ON request_logs(conversation_id);
