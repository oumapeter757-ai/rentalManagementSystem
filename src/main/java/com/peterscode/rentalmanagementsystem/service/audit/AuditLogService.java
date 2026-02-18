package com.peterscode.rentalmanagementsystem.service.audit;

import com.peterscode.rentalmanagementsystem.dto.response.AuditLogResponse;
import com.peterscode.rentalmanagementsystem.model.audit.AuditAction;
import com.peterscode.rentalmanagementsystem.model.audit.EntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Map;

public interface AuditLogService {
    
    void log(AuditAction action, EntityType entityType, Long entityId, String details);
    
    void log(AuditAction action, EntityType entityType, Long entityId, String details, String status);
    
    void logWithError(AuditAction action, EntityType entityType, Long entityId, String details, String error);
    
    Page<AuditLogResponse> getAllLogs(Pageable pageable);
    
    Page<AuditLogResponse> getLogsByFilters(
            String email,
            AuditAction action,
            EntityType entityType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );
    
    Page<AuditLogResponse> getLogsByUser(Long userId, Pageable pageable);
    
    Page<AuditLogResponse> getLogsByEntity(EntityType entityType, Long entityId, Pageable pageable);
    
    AuditLogResponse getLogById(Long id);
    
    Map<String, Object> getStatistics(LocalDateTime since);
    
    void cleanupOldLogs(int retentionDays);
}
