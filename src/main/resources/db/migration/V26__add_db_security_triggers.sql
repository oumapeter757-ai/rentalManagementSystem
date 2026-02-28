-- V26: Database Security Triggers (disabled for deployment)
-- Triggers use DELIMITER which is incompatible with Flyway.
-- Database security is enforced via application-level audit logging (AOP).
-- This migration is intentionally a no-op placeholder to preserve version numbering.
SELECT 1;