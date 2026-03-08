-- Add user_name and email to request_logs for richer audit trails
ALTER TABLE request_logs ADD COLUMN IF NOT EXISTS user_name VARCHAR(255);
ALTER TABLE request_logs ADD COLUMN IF NOT EXISTS email VARCHAR(255);
