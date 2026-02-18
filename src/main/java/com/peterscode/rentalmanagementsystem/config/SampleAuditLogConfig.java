package com.peterscode.rentalmanagementsystem.config;

import com.peterscode.rentalmanagementsystem.model.audit.AuditAction;
import com.peterscode.rentalmanagementsystem.model.audit.EntityType;
import com.peterscode.rentalmanagementsystem.service.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates sample audit logs on application startup for testing
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class SampleAuditLogConfig {

    private final AuditLogService auditLogService;

    @Bean
    public CommandLineRunner createSampleAuditLogs() {
        return args -> {
            try {
                // Create some sample audit logs for testing
                log.info("Creating sample audit logs...");
                
                auditLogService.log(
                    AuditAction.LOGIN,
                    EntityType.SYSTEM,
                    null,
                    "System started - Creating sample audit logs for testing"
                );
                
                log.info("Sample audit log created successfully");
            } catch (Exception e) {
                log.error("Failed to create sample audit logs: {}", e.getMessage());
            }
        };
    }
}
