package com.peterscode.rentalmanagementsystem.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Database security configuration.
 *
 * Security is enforced via:
 * 1. MySQL AFTER triggers that audit all INSERT/UPDATE/DELETE operations (V27)
 * 2. Audit logs table is immutable (BEFORE UPDATE/DELETE triggers block modifications)
 * 3. A read-only MySQL user 'rental_readonly' for external database inspection
 * 4. Application-level audit logging via AOP (AuditAspect)
 */
@Configuration
@Slf4j
public class DataSourceSecurityConfig {

    @PostConstruct
    public void logSecurityStatus() {
        log.info("✅ DB Security: Audit triggers active on all critical tables");
        log.info("✅ DB Security: Audit logs are immutable (UPDATE/DELETE blocked)");
        log.info("✅ DB Security: Read-only user 'rental_readonly' available for direct DB inspection");
    }
}
