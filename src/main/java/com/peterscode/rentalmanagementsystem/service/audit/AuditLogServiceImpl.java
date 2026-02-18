package com.peterscode.rentalmanagementsystem.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peterscode.rentalmanagementsystem.dto.response.AuditLogResponse;
import com.peterscode.rentalmanagementsystem.exception.ResourceNotFoundException;
import com.peterscode.rentalmanagementsystem.model.audit.AuditAction;
import com.peterscode.rentalmanagementsystem.model.audit.AuditLog;
import com.peterscode.rentalmanagementsystem.model.audit.EntityType;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.AuditLogRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Async
    @Transactional
    public void log(AuditAction action, EntityType entityType, Long entityId, String details) {
        log(action, entityType, entityId, details, "SUCCESS");
    }

    @Override
    @Async
    @Transactional
    public void log(AuditAction action, EntityType entityType, Long entityId, String details, String status) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .status(status)
                    .build();

            // Extract user from security context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String email = auth.getName();
                userRepository.findByEmail(email).ifPresent(user -> {
                    auditLog.setUser(user);
                    auditLog.setUsername(user.getUsername());
                });
            }

            // Extract IP and User-Agent from request
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    auditLog.setIpAddress(getClientIp(request));
                    auditLog.setUserAgent(request.getHeader("User-Agent"));
                }
            } catch (IllegalStateException e) {
                // Not in request context (e.g., scheduled task)
                log.debug("Not in request context, skipping IP/User-Agent extraction");
            }

            auditLogRepository.save(auditLog);
            log.debug("Audit log created: action={}, entityType={}, entityId={}", action, entityType, entityId);
            
        } catch (Exception e) {
            log.error("Failed to create audit log: {}", e.getMessage(), e);
            // Don't throw exception to avoid breaking the main operation
        }
    }

    @Override
    @Async
    @Transactional
    public void logWithError(AuditAction action, EntityType entityType, Long entityId, String details, String error) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .status("FAILURE")
                    .errorMessage(error)
                    .build();

            // Extract user from security context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String email = auth.getName();
                userRepository.findByEmail(email).ifPresent(user -> {
                    auditLog.setUser(user);
                    auditLog.setUsername(user.getUsername());
                });
            }

            // Extract IP and User-Agent
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    auditLog.setIpAddress(getClientIp(request));
                    auditLog.setUserAgent(request.getHeader("User-Agent"));
                }
            } catch (IllegalStateException e) {
                log.debug("Not in request context");
            }

            auditLogRepository.save(auditLog);
            
        } catch (Exception e) {
            log.error("Failed to create error audit log: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable)
                .map(AuditLogResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getLogsByFilters(
            String email,
            AuditAction action,
            EntityType entityType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        return auditLogRepository.findByFilters(email, action, entityType, startDate, endDate, pageable)
                .map(AuditLogResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getLogsByUser(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable)
                .map(AuditLogResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getLogsByEntity(EntityType entityType, Long entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable)
                .map(AuditLogResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogResponse getLogById(Long id) {
        AuditLog log = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit log not found"));
        return AuditLogResponse.fromEntity(log);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics(LocalDateTime since) {
        Map<String, Object> stats = new HashMap<>();
        
        // Total logs since date
        long totalLogs = auditLogRepository.countSince(since);
        stats.put("totalLogs", totalLogs);
        
        // Count by action
        List<Object[]> actionCounts = auditLogRepository.countByActionSince(since);
        Map<String, Long> byAction = new HashMap<>();
        for (Object[] row : actionCounts) {
            byAction.put(row[0].toString(), (Long) row[1]);
        }
        stats.put("byAction", byAction);
        
        // Recent activity
        stats.put("since", since);
        
        return stats;
    }

    @Override
    @Transactional
    public void cleanupOldLogs(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
        log.info("Cleaned up audit logs older than {} days", retentionDays);
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_CLIENT_IP",
            "REMOTE_ADDR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }
}
