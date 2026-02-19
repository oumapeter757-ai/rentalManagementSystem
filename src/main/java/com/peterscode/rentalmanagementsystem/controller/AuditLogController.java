package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.response.AuditLogResponse;
import com.peterscode.rentalmanagementsystem.model.audit.AuditAction;
import com.peterscode.rentalmanagementsystem.model.audit.DbSecurityViolation;
import com.peterscode.rentalmanagementsystem.model.audit.EntityType;
import com.peterscode.rentalmanagementsystem.repository.DbSecurityViolationRepository;
import com.peterscode.rentalmanagementsystem.service.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final DbSecurityViolationRepository securityViolationRepository;

    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        log.info("Fetching all audit logs: page={}, size={}", page, size);
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AuditLogResponse> logs = auditLogService.getAllLogs(pageable);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/filter")
    public ResponseEntity<Page<AuditLogResponse>> getFilteredLogs(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) EntityType entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Filtering audit logs: email={}, action={}, entityType={}", email, action, entityType);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogResponse> logs = auditLogService.getLogsByFilters(
                email, action, entityType, startDate, endDate, pageable
        );
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> getLogById(@PathVariable Long id) {
        log.info("Fetching audit log by id: {}", id);
        AuditLogResponse log = auditLogService.getLogById(id);
        return ResponseEntity.ok(log);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<AuditLogResponse>> getLogsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Fetching audit logs for user: {}", userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogResponse> logs = auditLogService.getLogsByUser(userId, pageable);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<Page<AuditLogResponse>> getLogsByEntity(
            @PathVariable EntityType entityType,
            @PathVariable Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Fetching audit logs for entity: type={}, id={}", entityType, entityId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogResponse> logs = auditLogService.getLogsByEntity(entityType, entityId, pageable);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        if (since == null) {
            since = LocalDateTime.now().minusDays(30);  // Default: last 30 days
        }
        log.info("Fetching audit statistics since: {}", since);
        Map<String, Object> stats = auditLogService.getStatistics(since);
        return ResponseEntity.ok(stats);
    }

    // ── Security Violations (blocked direct DB access attempts) ───────────

    @GetMapping("/security-violations")
    public ResponseEntity<Page<DbSecurityViolation>> getSecurityViolations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Fetching DB security violations: page={}, size={}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<DbSecurityViolation> violations = securityViolationRepository.findAllByOrderByBlockedAtDesc(pageable);
        return ResponseEntity.ok(violations);
    }

    @GetMapping("/security-violations/count")
    public ResponseEntity<Map<String, Object>> getSecurityViolationCount() {
        long total = securityViolationRepository.count();
        long last24h = securityViolationRepository.countSince(LocalDateTime.now().minusHours(24));
        long last7d = securityViolationRepository.countSince(LocalDateTime.now().minusDays(7));
        Map<String, Object> counts = new HashMap<>();
        counts.put("total", total);
        counts.put("last24h", last24h);
        counts.put("last7d", last7d);
        return ResponseEntity.ok(counts);
    }
}
