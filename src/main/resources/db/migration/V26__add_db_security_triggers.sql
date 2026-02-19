-- =============================================================================
-- V26: Database Security Triggers
-- Prevents direct INSERT/UPDATE/DELETE on critical tables unless the
-- session variable @app_authorized is set (only done by the Spring app).
-- Anyone using MySQL CLI, Workbench, phpMyAdmin etc. will be BLOCKED.
-- =============================================================================

-- A log table so we can track blocked attempts
CREATE TABLE IF NOT EXISTS db_security_violations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name  VARCHAR(100)  NOT NULL,
    operation   VARCHAR(10)   NOT NULL,  -- INSERT / UPDATE / DELETE
    attempted_by VARCHAR(100) NOT NULL,  -- MySQL CURRENT_USER()
    ip_address  VARCHAR(100),
    details     TEXT,
    blocked_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ───────────────── Helper: log + reject ─────────────────
-- We cannot create a stored procedure for SIGNAL in triggers easily,
-- so each trigger will inline the logic.
-- ─────────────────────────────────────────────────────────

-- ===================== USERS TABLE =====================
DROP TRIGGER IF EXISTS trg_users_insert_guard;
DELIMITER $$
CREATE TRIGGER trg_users_insert_guard
BEFORE INSERT ON users FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('users', 'INSERT', CURRENT_USER(), CONCAT('Blocked insert for email: ', NEW.email));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database insertion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_users_update_guard;
DELIMITER $$
CREATE TRIGGER trg_users_update_guard
BEFORE UPDATE ON users FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('users', 'UPDATE', CURRENT_USER(), CONCAT('Blocked update for user ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database modification is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_users_delete_guard;
DELIMITER $$
CREATE TRIGGER trg_users_delete_guard
BEFORE DELETE ON users FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('users', 'DELETE', CURRENT_USER(), CONCAT('Blocked delete for user ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database deletion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

-- ===================== PROPERTIES TABLE =====================
DROP TRIGGER IF EXISTS trg_properties_insert_guard;
DELIMITER $$
CREATE TRIGGER trg_properties_insert_guard
BEFORE INSERT ON properties FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('properties', 'INSERT', CURRENT_USER(), CONCAT('Blocked insert: ', NEW.title));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database insertion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_properties_update_guard;
DELIMITER $$
CREATE TRIGGER trg_properties_update_guard
BEFORE UPDATE ON properties FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('properties', 'UPDATE', CURRENT_USER(), CONCAT('Blocked update for property ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database modification is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_properties_delete_guard;
DELIMITER $$
CREATE TRIGGER trg_properties_delete_guard
BEFORE DELETE ON properties FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('properties', 'DELETE', CURRENT_USER(), CONCAT('Blocked delete for property ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database deletion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

-- ===================== PAYMENTS TABLE =====================
DROP TRIGGER IF EXISTS trg_payments_insert_guard;
DELIMITER $$
CREATE TRIGGER trg_payments_insert_guard
BEFORE INSERT ON payments FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('payments', 'INSERT', CURRENT_USER(), CONCAT('Blocked payment insert, amount: ', NEW.amount));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database insertion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_payments_update_guard;
DELIMITER $$
CREATE TRIGGER trg_payments_update_guard
BEFORE UPDATE ON payments FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('payments', 'UPDATE', CURRENT_USER(), CONCAT('Blocked update for payment ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database modification is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_payments_delete_guard;
DELIMITER $$
CREATE TRIGGER trg_payments_delete_guard
BEFORE DELETE ON payments FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('payments', 'DELETE', CURRENT_USER(), CONCAT('Blocked delete for payment ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database deletion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

-- ===================== LEASES TABLE =====================
DROP TRIGGER IF EXISTS trg_leases_insert_guard;
DELIMITER $$
CREATE TRIGGER trg_leases_insert_guard
BEFORE INSERT ON leases FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('leases', 'INSERT', CURRENT_USER(), 'Blocked lease insert');
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database insertion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_leases_update_guard;
DELIMITER $$
CREATE TRIGGER trg_leases_update_guard
BEFORE UPDATE ON leases FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('leases', 'UPDATE', CURRENT_USER(), CONCAT('Blocked update for lease ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database modification is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_leases_delete_guard;
DELIMITER $$
CREATE TRIGGER trg_leases_delete_guard
BEFORE DELETE ON leases FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('leases', 'DELETE', CURRENT_USER(), CONCAT('Blocked delete for lease ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database deletion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

-- ===================== BOOKINGS TABLE =====================
DROP TRIGGER IF EXISTS trg_bookings_insert_guard;
DELIMITER $$
CREATE TRIGGER trg_bookings_insert_guard
BEFORE INSERT ON bookings FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('bookings', 'INSERT', CURRENT_USER(), 'Blocked booking insert');
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database insertion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_bookings_update_guard;
DELIMITER $$
CREATE TRIGGER trg_bookings_update_guard
BEFORE UPDATE ON bookings FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('bookings', 'UPDATE', CURRENT_USER(), CONCAT('Blocked update for booking ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database modification is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_bookings_delete_guard;
DELIMITER $$
CREATE TRIGGER trg_bookings_delete_guard
BEFORE DELETE ON bookings FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('bookings', 'DELETE', CURRENT_USER(), CONCAT('Blocked delete for booking ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database deletion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

-- ===================== RENTAL_APPLICATIONS TABLE =====================
DROP TRIGGER IF EXISTS trg_rental_applications_insert_guard;
DELIMITER $$
CREATE TRIGGER trg_rental_applications_insert_guard
BEFORE INSERT ON rental_applications FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('rental_applications', 'INSERT', CURRENT_USER(), 'Blocked application insert');
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database insertion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_rental_applications_update_guard;
DELIMITER $$
CREATE TRIGGER trg_rental_applications_update_guard
BEFORE UPDATE ON rental_applications FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('rental_applications', 'UPDATE', CURRENT_USER(), CONCAT('Blocked update ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database modification is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_rental_applications_delete_guard;
DELIMITER $$
CREATE TRIGGER trg_rental_applications_delete_guard
BEFORE DELETE ON rental_applications FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('rental_applications', 'DELETE', CURRENT_USER(), CONCAT('Blocked delete ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database deletion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

-- ===================== MAINTENANCE_REQUESTS TABLE =====================
DROP TRIGGER IF EXISTS trg_maintenance_requests_insert_guard;
DELIMITER $$
CREATE TRIGGER trg_maintenance_requests_insert_guard
BEFORE INSERT ON maintenance_requests FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('maintenance_requests', 'INSERT', CURRENT_USER(), 'Blocked maintenance insert');
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database insertion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_maintenance_requests_update_guard;
DELIMITER $$
CREATE TRIGGER trg_maintenance_requests_update_guard
BEFORE UPDATE ON maintenance_requests FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('maintenance_requests', 'UPDATE', CURRENT_USER(), CONCAT('Blocked update ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database modification is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_maintenance_requests_delete_guard;
DELIMITER $$
CREATE TRIGGER trg_maintenance_requests_delete_guard
BEFORE DELETE ON maintenance_requests FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('maintenance_requests', 'DELETE', CURRENT_USER(), CONCAT('Blocked delete ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database deletion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

-- ===================== MESSAGES TABLE =====================
DROP TRIGGER IF EXISTS trg_messages_insert_guard;
DELIMITER $$
CREATE TRIGGER trg_messages_insert_guard
BEFORE INSERT ON messages FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('messages', 'INSERT', CURRENT_USER(), 'Blocked message insert');
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database insertion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_messages_update_guard;
DELIMITER $$
CREATE TRIGGER trg_messages_update_guard
BEFORE UPDATE ON messages FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('messages', 'UPDATE', CURRENT_USER(), CONCAT('Blocked update ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database modification is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_messages_delete_guard;
DELIMITER $$
CREATE TRIGGER trg_messages_delete_guard
BEFORE DELETE ON messages FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('messages', 'DELETE', CURRENT_USER(), CONCAT('Blocked delete ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database deletion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

-- ===================== MONTHLY_PAYMENT_HISTORY TABLE =====================
DROP TRIGGER IF EXISTS trg_monthly_payment_history_insert_guard;
DELIMITER $$
CREATE TRIGGER trg_monthly_payment_history_insert_guard
BEFORE INSERT ON monthly_payment_history FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('monthly_payment_history', 'INSERT', CURRENT_USER(), 'Blocked payment history insert');
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database insertion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_monthly_payment_history_update_guard;
DELIMITER $$
CREATE TRIGGER trg_monthly_payment_history_update_guard
BEFORE UPDATE ON monthly_payment_history FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('monthly_payment_history', 'UPDATE', CURRENT_USER(), CONCAT('Blocked update ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database modification is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_monthly_payment_history_delete_guard;
DELIMITER $$
CREATE TRIGGER trg_monthly_payment_history_delete_guard
BEFORE DELETE ON monthly_payment_history FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('monthly_payment_history', 'DELETE', CURRENT_USER(), CONCAT('Blocked delete ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Direct database deletion is not allowed. Use the application.';
    END IF;
END$$
DELIMITER ;

-- ===================== AUDIT_LOGS TABLE =====================
-- Audit logs: protect UPDATE and DELETE, but allow INSERT (even from triggers above)
DROP TRIGGER IF EXISTS trg_audit_logs_update_guard;
DELIMITER $$
CREATE TRIGGER trg_audit_logs_update_guard
BEFORE UPDATE ON audit_logs FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('audit_logs', 'UPDATE', CURRENT_USER(), CONCAT('Blocked update ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Audit logs cannot be modified. They are immutable.';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_audit_logs_delete_guard;
DELIMITER $$
CREATE TRIGGER trg_audit_logs_delete_guard
BEFORE DELETE ON audit_logs FOR EACH ROW
BEGIN
    IF @app_authorized IS NULL OR @app_authorized != 'RENTAL_APP_V1' THEN
        INSERT INTO db_security_violations(table_name, operation, attempted_by, details)
        VALUES ('audit_logs', 'DELETE', CURRENT_USER(), CONCAT('Blocked delete ID: ', OLD.id));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Audit logs cannot be deleted. They are immutable.';
    END IF;
END$$
DELIMITER ;

