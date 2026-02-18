-- 1. Create users table first (no dependencies)


CREATE TABLE users (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       username VARCHAR(50) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       first_name VARCHAR(100),
                       last_name VARCHAR(100),
                       phone_number VARCHAR(100),
                       role VARCHAR(20) NOT NULL,
                       enabled BOOLEAN DEFAULT TRUE,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. Create email_verification_tokens (depends on users)
CREATE TABLE email_verification_tokens (
                                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                           token VARCHAR(255) NOT NULL UNIQUE,
                                           used BOOLEAN DEFAULT FALSE,
                                           user_id BIGINT NOT NULL,
                                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                           updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                           FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 3. Create password_reset_tokens (depends on users)
CREATE TABLE password_reset_tokens (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       token VARCHAR(255) NOT NULL UNIQUE,
                                       user_id BIGINT NOT NULL,
                                       expiry_date DATETIME NOT NULL,
                                       used BOOLEAN DEFAULT FALSE NOT NULL,
                                       used_at DATETIME NULL,
                                       created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                       FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                       INDEX idx_token (token),
                                       INDEX idx_user_id (user_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS email_logs (
                                          id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                          recipient VARCHAR(255) NOT NULL,
                                          subject VARCHAR(500) NOT NULL,
                                          body TEXT,
                                          template_name VARCHAR(100),
                                          status VARCHAR(20) NOT NULL,
                                          retry_count INT NOT NULL DEFAULT 0,
                                          sent_at TIMESTAMP NULL,
                                          last_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          error_message TEXT,
                                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                          INDEX idx_email_logs_recipient (recipient),
                                          INDEX idx_email_logs_status (status),
                                          INDEX idx_email_logs_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;