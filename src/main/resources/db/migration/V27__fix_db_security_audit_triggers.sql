-- V27: Audit trigger replacement (disabled for deployment)
-- Triggers use DELIMITER which is incompatible with Flyway.
-- Audit logging is handled at the application level via Spring AOP.
-- This migration is intentionally a no-op placeholder to preserve version numbering.
SELECT 1;