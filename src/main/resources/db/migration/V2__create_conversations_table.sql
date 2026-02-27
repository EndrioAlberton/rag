-- Create conversations table
CREATE TABLE IF NOT EXISTS conversations (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    owner_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_activity TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_conversations_owner ON conversations(owner_id);
CREATE INDEX IF NOT EXISTS idx_conversations_last_activity ON conversations(last_activity);
