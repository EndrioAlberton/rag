-- Remove is_shared column from conversations table
ALTER TABLE conversations DROP COLUMN IF EXISTS is_shared;
