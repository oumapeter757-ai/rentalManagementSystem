-- Create monthly payment history table to track payments per month
CREATE TABLE IF NOT EXISTS monthly_payment_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    lease_id BIGINT,
    month INT NOT NULL,
    year INT NOT NULL,
    total_due DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    total_paid DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_deadline DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE CASCADE,
    FOREIGN KEY (lease_id) REFERENCES leases(id) ON DELETE SET NULL,
    UNIQUE KEY unique_tenant_month_year (tenant_id, month, year),
    INDEX idx_tenant_year_month (tenant_id, year, month),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add property_id column to payments if it doesn't exist
SET @column_check = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'property_id');
SET @alter_sql = IF(@column_check = 0,
    'ALTER TABLE payments ADD COLUMN property_id BIGINT AFTER lease_id',
    'SELECT "property_id column already exists"');
PREPARE stmt FROM @alter_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add foreign key constraint
SET @fk_check = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND CONSTRAINT_NAME = 'fk_payment_property');
SET @fk_sql = IF(@fk_check = 0,
    'ALTER TABLE payments ADD CONSTRAINT fk_payment_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE SET NULL',
    'SELECT "fk_payment_property already exists"');
PREPARE stmt FROM @fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
