-- V9: Add property_id and updated_at columns to maintenance_requests
-- The table was created in V5, this migration adds missing columns
-- Add property_id column
SET @col_exists = (
        SELECT COUNT(*)
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'maintenance_requests'
            AND COLUMN_NAME = 'property_id'
    );
SET @sql = IF(
        @col_exists = 0,
        'ALTER TABLE maintenance_requests ADD COLUMN property_id BIGINT NULL',
        'SELECT 1'
    );
PREPARE stmt
FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
-- Add FK for property_id
SET @fk_exists = (
        SELECT COUNT(*)
        FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE()
            AND CONSTRAINT_NAME = 'fk_maintenance_property'
            AND TABLE_NAME = 'maintenance_requests'
    );
SET @sql2 = IF(
        @fk_exists = 0,
        'ALTER TABLE maintenance_requests ADD CONSTRAINT fk_maintenance_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE CASCADE',
        'SELECT 1'
    );
PREPARE stmt2
FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;