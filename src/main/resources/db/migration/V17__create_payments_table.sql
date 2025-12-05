
CREATE TABLE IF NOT EXISTS payments (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        tenant_id BIGINT NOT NULL,
                                        phone_number VARCHAR(13),
    amount DECIMAL(19,2) NOT NULL,
    method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_code VARCHAR(100),
    gateway_response TEXT,
    callback_received BOOLEAN DEFAULT FALSE,
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    notes TEXT,
    FOREIGN KEY (tenant_id) REFERENCES users(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;