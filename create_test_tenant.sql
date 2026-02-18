-- Create verified test tenant account
-- Password: Test123! (BCrypt hash)
INSERT INTO users (email, username, password, first_name, last_name, phone_number, role, enabled, created_at)
VALUES (
    'tenant.test@rentalhub.com',
    'tenant_test',
    '$2a$10$YQ5HZ5YxYQJQy5ZNc5ZqYeK5xYQJQy5ZNc5ZqYK5xYQJQy5ZNc5Zq',  -- Test123!
    'Test',
    'Tenant',
    '+254712345678',
    'TENANT',
    true,
    NOW()
);

-- Note: You'll need to update the password hash with the actual BCrypt hash from your system
-- The above is a placeholder. Use the AuthController to register and then manually verify via DB
