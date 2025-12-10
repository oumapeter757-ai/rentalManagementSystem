\
CREATE TABLE IF NOT EXISTS maintenance_images (
                                                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                  maintenance_request_id BIGINT NOT NULL,
                                                  image_url VARCHAR(500) NOT NULL,
                                                  thumbnail_url VARCHAR(500) NULL,
                                                  caption VARCHAR(200) NULL,
                                                  uploaded_by VARCHAR(255) NULL,
                                                  uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  deleted BOOLEAN DEFAULT FALSE,
                                                  file_name VARCHAR(255) NULL,
                                                  file_size BIGINT NULL,
                                                  file_type VARCHAR(50) NULL,

                                                  CONSTRAINT fk_maintenance_image_request FOREIGN KEY (maintenance_request_id) REFERENCES maintenance_requests(id) ON DELETE CASCADE,

                                                  INDEX idx_maintenance_image_request (maintenance_request_id),
                                                  INDEX idx_maintenance_image_uploaded_at (uploaded_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;