-- V21: Make updated_at ON UPDATE CURRENT_TIMESTAMP (it was created in V1 without ON UPDATE)
-- This is safe because the column already exists
ALTER TABLE users
MODIFY COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;