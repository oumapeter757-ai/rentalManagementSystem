package com.peterscode.rentalmanagementsystem.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Database security configuration.
 *
 * The actual connection-init-sql is set in application.yml:
 *   spring.datasource.hikari.connection-init-sql: "SET @app_authorized = 'RENTAL_APP_V1'"
 *
 * This works with MySQL triggers (V26__add_db_security_triggers.sql) that reject
 * any INSERT/UPDATE/DELETE when @app_authorized is NOT set — blocking direct DB access
 * via MySQL CLI, Workbench, phpMyAdmin, etc.
 */
@Configuration
@Slf4j
public class DataSourceSecurityConfig {

    @PostConstruct
    public void logSecurityStatus() {
        log.info("✅ Database security: @app_authorized is set via HikariCP connection-init-sql");
        log.info("   Direct DB access (CLI, Workbench, phpMyAdmin) will be BLOCKED by triggers");
    }
}
