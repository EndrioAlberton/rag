-- Add orion_user_hash column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS orion_user_hash VARCHAR(255) UNIQUE;

CREATE INDEX IF NOT EXISTS idx_users_orion_hash ON users(orion_user_hash);
