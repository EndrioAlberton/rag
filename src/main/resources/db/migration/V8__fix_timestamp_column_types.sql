-- Fix column types to match Hibernate's expected mapping for LocalDateTime -> datetime(6)
-- and @Enumerated(EnumType.STRING) -> enum

ALTER TABLE chat_messages MODIFY COLUMN timestamp DATETIME(6) NOT NULL;
ALTER TABLE chat_messages MODIFY COLUMN type ENUM('ASSISTANT','SYSTEM','USER') NOT NULL;
ALTER TABLE conversations MODIFY COLUMN created_at DATETIME(6) NOT NULL;
ALTER TABLE conversations MODIFY COLUMN last_activity DATETIME(6) NOT NULL;
ALTER TABLE users MODIFY COLUMN created_at DATETIME(6) NOT NULL;
ALTER TABLE users MODIFY COLUMN last_login DATETIME(6);
