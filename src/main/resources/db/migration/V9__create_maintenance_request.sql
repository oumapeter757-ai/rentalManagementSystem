CREATE TABLE IF NOT EXISTS maintenance_requests (
                                                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                    tenant_id BIGINT NOT NULL,
                                                    category VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes TEXT NULL,
    request_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_maintenance_request_tenant
    FOREIGN KEY (tenant_id)
    REFERENCES users(id)
    ON DELETE CASCADE,

    INDEX idx_maintenance_tenant (tenant_id),
    INDEX idx_maintenance_status (status),
    INDEX idx_maintenance_category (category),
    INDEX idx_maintenance_priority (priority),
    INDEX idx_maintenance_request_date (request_date DESC)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;