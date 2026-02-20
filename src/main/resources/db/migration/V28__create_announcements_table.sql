-- V28: Create announcements table for landlord/admin marquee notifications
CREATE TABLE IF NOT EXISTS announcements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message TEXT NOT NULL,
    created_by_id BIGINT NOT NULL,
    created_by_name VARCHAR(100),
    created_by_role VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    INDEX idx_announcements_active (active),
    INDEX idx_announcements_expires (expires_at)
);

