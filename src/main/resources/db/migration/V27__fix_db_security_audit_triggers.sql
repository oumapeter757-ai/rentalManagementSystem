-- =============================================================================
-- V27: Disable DB Security Triggers
-- The trigger-based approach using session variables is unreliable with
-- connection pooling (HikariCP). Instead, database security should be
-- enforced via:
--   1. A separate read-only MySQL user for direct DB access
--   2. Revoking INSERT/UPDATE/DELETE from that user
--   3. Application audit logging (already implemented via AOP)
--   4. The app user retains full privileges
-- =============================================================================

-- Drop all security guard triggers from V26
DROP TRIGGER IF EXISTS trg_users_insert_guard;
DROP TRIGGER IF EXISTS trg_users_update_guard;
DROP TRIGGER IF EXISTS trg_users_delete_guard;

DROP TRIGGER IF EXISTS trg_properties_insert_guard;
DROP TRIGGER IF EXISTS trg_properties_update_guard;
DROP TRIGGER IF EXISTS trg_properties_delete_guard;

DROP TRIGGER IF EXISTS trg_payments_insert_guard;
DROP TRIGGER IF EXISTS trg_payments_update_guard;
DROP TRIGGER IF EXISTS trg_payments_delete_guard;

DROP TRIGGER IF EXISTS trg_leases_insert_guard;
DROP TRIGGER IF EXISTS trg_leases_update_guard;
DROP TRIGGER IF EXISTS trg_leases_delete_guard;

DROP TRIGGER IF EXISTS trg_bookings_insert_guard;
DROP TRIGGER IF EXISTS trg_bookings_update_guard;
DROP TRIGGER IF EXISTS trg_bookings_delete_guard;

DROP TRIGGER IF EXISTS trg_rental_applications_insert_guard;
DROP TRIGGER IF EXISTS trg_rental_applications_update_guard;
DROP TRIGGER IF EXISTS trg_rental_applications_delete_guard;

DROP TRIGGER IF EXISTS trg_maintenance_requests_insert_guard;
DROP TRIGGER IF EXISTS trg_maintenance_requests_update_guard;
DROP TRIGGER IF EXISTS trg_maintenance_requests_delete_guard;

DROP TRIGGER IF EXISTS trg_messages_insert_guard;
DROP TRIGGER IF EXISTS trg_messages_update_guard;
DROP TRIGGER IF EXISTS trg_messages_delete_guard;

DROP TRIGGER IF EXISTS trg_monthly_payment_history_insert_guard;
DROP TRIGGER IF EXISTS trg_monthly_payment_history_update_guard;
DROP TRIGGER IF EXISTS trg_monthly_payment_history_delete_guard;

DROP TRIGGER IF EXISTS trg_audit_logs_update_guard;
DROP TRIGGER IF EXISTS trg_audit_logs_delete_guard;

-- =============================================================================
-- REPLACEMENT: Audit-only triggers that LOG changes (don't block them)
-- Every INSERT/UPDATE/DELETE on critical tables is recorded in db_security_violations
-- as an audit trail, but the operation is ALLOWED to proceed.
-- =============================================================================

-- Users: log all changes
DROP TRIGGER IF EXISTS trg_users_audit_insert;
DELIMITER $$
CREATE TRIGGER trg_users_audit_insert
AFTER INSERT ON users FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('users', 'INSERT', CURRENT_USER(), CONCAT('New user: ', NEW.email, ' (id:', NEW.id, ')'), NOW());
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_users_audit_update;
DELIMITER $$
CREATE TRIGGER trg_users_audit_update
AFTER UPDATE ON users FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('users', 'UPDATE', CURRENT_USER(), CONCAT('Updated user id:', OLD.id, ' email:', OLD.email), NOW());
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_users_audit_delete;
DELIMITER $$
CREATE TRIGGER trg_users_audit_delete
AFTER DELETE ON users FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('users', 'DELETE', CURRENT_USER(), CONCAT('Deleted user id:', OLD.id, ' email:', OLD.email), NOW());
END$$
DELIMITER ;

-- Payments: log all changes
DROP TRIGGER IF EXISTS trg_payments_audit_insert;
DELIMITER $$
CREATE TRIGGER trg_payments_audit_insert
AFTER INSERT ON payments FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('payments', 'INSERT', CURRENT_USER(), CONCAT('Payment id:', NEW.id, ' amount:', NEW.amount), NOW());
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_payments_audit_update;
DELIMITER $$
CREATE TRIGGER trg_payments_audit_update
AFTER UPDATE ON payments FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('payments', 'UPDATE', CURRENT_USER(), CONCAT('Payment id:', OLD.id, ' old_amount:', OLD.amount), NOW());
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_payments_audit_delete;
DELIMITER $$
CREATE TRIGGER trg_payments_audit_delete
AFTER DELETE ON payments FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('payments', 'DELETE', CURRENT_USER(), CONCAT('Deleted payment id:', OLD.id), NOW());
END$$
DELIMITER ;

-- Properties: log all changes
DROP TRIGGER IF EXISTS trg_properties_audit_insert;
DELIMITER $$
CREATE TRIGGER trg_properties_audit_insert
AFTER INSERT ON properties FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('properties', 'INSERT', CURRENT_USER(), CONCAT('Property: ', NEW.title, ' (id:', NEW.id, ')'), NOW());
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_properties_audit_update;
DELIMITER $$
CREATE TRIGGER trg_properties_audit_update
AFTER UPDATE ON properties FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('properties', 'UPDATE', CURRENT_USER(), CONCAT('Property id:', OLD.id, ' title:', OLD.title), NOW());
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_properties_audit_delete;
DELIMITER $$
CREATE TRIGGER trg_properties_audit_delete
AFTER DELETE ON properties FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('properties', 'DELETE', CURRENT_USER(), CONCAT('Deleted property id:', OLD.id), NOW());
END$$
DELIMITER ;

-- Leases: log all changes
DROP TRIGGER IF EXISTS trg_leases_audit_insert;
DELIMITER $$
CREATE TRIGGER trg_leases_audit_insert
AFTER INSERT ON leases FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('leases', 'INSERT', CURRENT_USER(), CONCAT('Lease id:', NEW.id), NOW());
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_leases_audit_update;
DELIMITER $$
CREATE TRIGGER trg_leases_audit_update
AFTER UPDATE ON leases FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('leases', 'UPDATE', CURRENT_USER(), CONCAT('Lease id:', OLD.id), NOW());
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_leases_audit_delete;
DELIMITER $$
CREATE TRIGGER trg_leases_audit_delete
AFTER DELETE ON leases FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('leases', 'DELETE', CURRENT_USER(), CONCAT('Deleted lease id:', OLD.id), NOW());
END$$
DELIMITER ;

-- Bookings: log all changes
DROP TRIGGER IF EXISTS trg_bookings_audit_insert;
DELIMITER $$
CREATE TRIGGER trg_bookings_audit_insert
AFTER INSERT ON bookings FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('bookings', 'INSERT', CURRENT_USER(), CONCAT('Booking id:', NEW.id), NOW());
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_bookings_audit_update;
DELIMITER $$
CREATE TRIGGER trg_bookings_audit_update
AFTER UPDATE ON bookings FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('bookings', 'UPDATE', CURRENT_USER(), CONCAT('Booking id:', OLD.id), NOW());
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_bookings_audit_delete;
DELIMITER $$
CREATE TRIGGER trg_bookings_audit_delete
AFTER DELETE ON bookings FOR EACH ROW
BEGIN
    INSERT INTO db_security_violations(table_name, operation, attempted_by, details, blocked_at)
    VALUES ('bookings', 'DELETE', CURRENT_USER(), CONCAT('Deleted booking id:', OLD.id), NOW());
END$$
DELIMITER ;

-- Audit logs: prevent UPDATE and DELETE (these are immutable)
DROP TRIGGER IF EXISTS trg_audit_logs_prevent_update;
DELIMITER $$
CREATE TRIGGER trg_audit_logs_prevent_update
BEFORE UPDATE ON audit_logs FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Audit logs are immutable and cannot be modified.';
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_audit_logs_prevent_delete;
DELIMITER $$
CREATE TRIGGER trg_audit_logs_prevent_delete
BEFORE DELETE ON audit_logs FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Audit logs are immutable and cannot be deleted.';
END$$
DELIMITER ;

-- =============================================================================
-- Create a READ-ONLY MySQL user for external/direct database access
-- Anyone who needs to inspect the database directly should use this user.
-- This user CANNOT insert, update, or delete data.
-- =============================================================================

-- Create read-only user (skip if exists)
-- Note: Change the password in production!
CREATE USER IF NOT EXISTS 'rental_readonly'@'localhost' IDENTIFIED BY 'ReadOnly_2026!';
CREATE USER IF NOT EXISTS 'rental_readonly'@'%' IDENTIFIED BY 'ReadOnly_2026!';

-- Grant SELECT only
GRANT SELECT ON rental_management.* TO 'rental_readonly'@'localhost';
GRANT SELECT ON rental_management.* TO 'rental_readonly'@'%';

-- Revoke any write privileges (safety net)
REVOKE INSERT, UPDATE, DELETE, DROP, ALTER, CREATE ON rental_management.* FROM 'rental_readonly'@'localhost';
REVOKE INSERT, UPDATE, DELETE, DROP, ALTER, CREATE ON rental_management.* FROM 'rental_readonly'@'%';

FLUSH PRIVILEGES;

