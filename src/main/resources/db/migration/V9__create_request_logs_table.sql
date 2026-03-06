-- Request logging system for traceability (issue #10)
CREATE TABLE IF NOT EXISTS request_logs (
    id VARCHAR(255) PRIMARY KEY,
    phone_number VARCHAR(50),
    user_id VARCHAR(255) NOT NULL,
    user_message TEXT NOT NULL,
    message_timestamp TIMESTAMP(6) NOT NULL,
    rag_result TEXT,
    rag_latency_ms BIGINT,
    llm_response TEXT,
    llm_latency_ms BIGINT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_request_logs_user_id ON request_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_request_logs_message_timestamp ON request_logs(message_timestamp);
CREATE INDEX IF NOT EXISTS idx_request_logs_phone_number ON request_logs(phone_number);
