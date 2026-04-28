-- Add handoff flags + feedback for analytics/dashboard

ALTER TABLE request_logs
    ADD COLUMN IF NOT EXISTS rag_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS handoff_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS handoff_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_request_logs_handoff_required ON request_logs(handoff_required);
CREATE INDEX IF NOT EXISTS idx_request_logs_rag_score ON request_logs(rag_score);

CREATE TABLE IF NOT EXISTS request_log_feedback (
    id VARCHAR(255) PRIMARY KEY,
    request_log_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    conversation_id VARCHAR(255),
    -- Snapshot: question/answer + boolean like/dislike
    liked BOOLEAN,
    user_message TEXT,
    llm_response TEXT,
    value VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (request_log_id) REFERENCES request_logs(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_request_log_feedback_request_log_id ON request_log_feedback(request_log_id);
CREATE INDEX IF NOT EXISTS idx_request_log_feedback_user_id ON request_log_feedback(user_id);
CREATE INDEX IF NOT EXISTS idx_request_log_feedback_conversation_id ON request_log_feedback(conversation_id);
CREATE INDEX IF NOT EXISTS idx_request_log_feedback_created_at ON request_log_feedback(created_at);
CREATE INDEX IF NOT EXISTS idx_request_log_feedback_liked ON request_log_feedback(liked);

