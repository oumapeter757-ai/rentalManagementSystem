CREATE TABLE bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    deposit_paid BOOLEAN DEFAULT FALSE,
    rent_paid BOOLEAN DEFAULT FALSE,
    start_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    payment_deadline DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking_tenant FOREIGN KEY (tenant_id) REFERENCES users(id),
    CONSTRAINT fk_booking_property FOREIGN KEY (property_id) REFERENCES properties(id),
    INDEX idx_booking_tenant (tenant_id),
    INDEX idx_booking_property (property_id),
    INDEX idx_booking_status (status)
);
