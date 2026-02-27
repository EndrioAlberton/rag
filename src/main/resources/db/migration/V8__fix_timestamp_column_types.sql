-- Fix column types to match Hibernate's expected mapping for LocalDateTime -> timestamp(6)
-- and @Enumerated(EnumType.STRING) -> varchar

ALTER TABLE chat_messages ALTER COLUMN timestamp TYPE TIMESTAMP(6);
ALTER TABLE chat_messages ALTER COLUMN type TYPE VARCHAR(50);
ALTER TABLE conversations ALTER COLUMN created_at TYPE TIMESTAMP(6);
ALTER TABLE conversations ALTER COLUMN last_activity TYPE TIMESTAMP(6);
ALTER TABLE users ALTER COLUMN created_at TYPE TIMESTAMP(6);
ALTER TABLE users ALTER COLUMN last_login TYPE TIMESTAMP(6);
