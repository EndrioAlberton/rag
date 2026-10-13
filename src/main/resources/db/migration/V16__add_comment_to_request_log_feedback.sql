-- Free-text report written by the user about a specific answer (value = 'REPORT')
ALTER TABLE request_log_feedback ADD COLUMN IF NOT EXISTS comment TEXT;
