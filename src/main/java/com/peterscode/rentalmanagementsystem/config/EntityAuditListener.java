package com.peterscode.rentalmanagementsystem.config;

import com.peterscode.rentalmanagementsystem.model.audit.AuditAction;
import com.peterscode.rentalmanagementsystem.model.audit.EntityType;
import com.peterscode.rentalmanagementsystem.service.audit.AuditLogService;
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA Entity Listener to automatically audit ALL database changes
 * This captures CREATE, UPDATE, DELETE operations on all entities
 */
@Slf4j
@Component
public class EntityAuditListener {

    private static AuditLogService auditLogService;

    @Autowired
    public void setAuditLogService(AuditLogService auditLogService) {
        EntityAuditListener.auditLogService = auditLogService;
    }

    @PostPersist
    public void afterCreate(Object entity) {
        if (auditLogService != null && !isAuditEntity(entity)) {
            try {
                EntityType entityType = getEntityType(entity);
                Long entityId = getEntityId(entity);

                auditLogService.log(
                    AuditAction.CREATE,
                    entityType,
                    entityId,
                    String.format("Created %s with ID: %d", entityType, entityId)
                );

                log.debug("Audited CREATE for {} ID: {}", entityType, entityId);
            } catch (Exception e) {
                log.warn("Failed to audit CREATE operation: {}", e.getMessage());
            }
        }
    }

    @PostUpdate
    public void afterUpdate(Object entity) {
        if (auditLogService != null && !isAuditEntity(entity)) {
            try {
                EntityType entityType = getEntityType(entity);
                Long entityId = getEntityId(entity);

                auditLogService.log(
                    AuditAction.UPDATE,
                    entityType,
                    entityId,
                    String.format("Updated %s with ID: %d", entityType, entityId)
                );

                log.debug("Audited UPDATE for {} ID: {}", entityType, entityId);
            } catch (Exception e) {
                log.warn("Failed to audit UPDATE operation: {}", e.getMessage());
            }
        }
    }

    @PostRemove
    public void afterDelete(Object entity) {
        if (auditLogService != null && !isAuditEntity(entity)) {
            try {
                EntityType entityType = getEntityType(entity);
                Long entityId = getEntityId(entity);

                auditLogService.log(
                    AuditAction.DELETE,
                    entityType,
                    entityId,
                    String.format("Deleted %s with ID: %d", entityType, entityId)
                );

                log.debug("Audited DELETE for {} ID: {}", entityType, entityId);
            } catch (Exception e) {
                log.warn("Failed to audit DELETE operation: {}", e.getMessage());
            }
        }
    }

    /**
     * Check if entity is an audit entity to prevent circular logging
     */
    private boolean isAuditEntity(Object entity) {
        String className = entity.getClass().getSimpleName();
        return className.equals("AuditLog") ||
               className.equals("VerificationToken") ||
               className.equals("PasswordResetToken");
    }

    /**
     * Extract entity type from the entity class name
     */
    private EntityType getEntityType(Object entity) {
        String className = entity.getClass().getSimpleName();

        try {
            // Try to match with existing EntityType enum
            return switch (className) {
                case "User" -> EntityType.USER;
                case "Property" -> EntityType.PROPERTY;
                case "Lease" -> EntityType.LEASE;
                case "Payment" -> EntityType.PAYMENT;
                case "RentalApplication" -> EntityType.APPLICATION;
                case "MaintenanceRequest" -> EntityType.MAINTENANCE_REQUEST;
                case "Message" -> EntityType.MESSAGE;
                case "Booking" -> EntityType.BOOKING;
                default -> EntityType.SYSTEM;
            };
        } catch (Exception e) {
            return EntityType.SYSTEM;
        }
    }

    /**
     * Extract entity ID using reflection
     */
    private Long getEntityId(Object entity) {
        try {
            java.lang.reflect.Method getIdMethod = entity.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(entity);
            if (id instanceof Long) {
                return (Long) id;
            }
        } catch (Exception e) {
            log.debug("Could not extract ID from entity: {}", e.getMessage());
        }
        return null;
    }
}

