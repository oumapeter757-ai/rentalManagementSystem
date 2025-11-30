CREATE TABLE IF NOT EXISTS properties (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          title VARCHAR(150) NOT NULL,
                                          description VARCHAR(500),
                                          location VARCHAR(100) NOT NULL,
                                          address VARCHAR(100) NOT NULL,
                                          rent_amount DECIMAL(10,2) NOT NULL,
                                          type VARCHAR(50) NOT NULL,
                                          bedrooms INT NOT NULL,
                                          bathrooms INT NOT NULL,
                                          furnished BOOLEAN NOT NULL,
                                          available BOOLEAN DEFAULT TRUE,
                                          size DOUBLE,
                                          main_image_url VARCHAR(255),
                                          owner_id BIGINT NOT NULL,
                                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                          FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS property_images (
                                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                               property_id BIGINT NOT NULL,
                                               file_url VARCHAR(500) NOT NULL,
                                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                               FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS property_amenities (
                                                  property_id BIGINT NOT NULL,
                                                  amenity VARCHAR(255),
                                                  FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE CASCADE,
                                                  PRIMARY KEY (property_id, amenity)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;