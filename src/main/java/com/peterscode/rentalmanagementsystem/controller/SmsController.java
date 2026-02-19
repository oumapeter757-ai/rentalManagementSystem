package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.model.audit.AuditAction;
import com.peterscode.rentalmanagementsystem.model.audit.EntityType;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import com.peterscode.rentalmanagementsystem.service.audit.AuditLogService;
import com.peterscode.rentalmanagementsystem.service.sms.SmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sms")
@RequiredArgsConstructor
@Tag(name = "SMS", description = "SMS notification and announcement APIs")
public class SmsController {

    private final SmsService smsService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Send SMS to a single number")
    public ResponseEntity<ApiResponse<String>> sendSms(@RequestBody Map<String, String> payload) {
        String to = payload.get("to");
        String message = payload.get("message");

        if (to == null || message == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Missing 'to' or 'message' field"));
        }

        smsService.sendSms(to, message);
        auditLogService.log(AuditAction.CREATE, EntityType.SYSTEM, null,
                "SMS sent to " + to);
        return ResponseEntity.ok(ApiResponse.success("SMS sent successfully", null));
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Send SMS announcement to all tenants at once")
    public ResponseEntity<ApiResponse<Map<String, Object>>> broadcastToTenants(
            @RequestBody Map<String, String> payload) {
        String message = payload.get("message");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Message is required"));
        }

        List<User> tenants = userRepository.findByRoleAndEnabled(Role.TENANT, true);

        int sent = 0;
        int failed = 0;

        for (User tenant : tenants) {
            try {
                if (tenant.getPhoneNumber() != null && !tenant.getPhoneNumber().isEmpty()) {
                    smsService.sendSms(tenant.getPhoneNumber(), message);
                    sent++;
                } else {
                    failed++;
                    log.warn("Tenant {} has no phone number", tenant.getEmail());
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to send SMS to tenant {}: {}", tenant.getEmail(), e.getMessage());
            }
        }

        auditLogService.log(AuditAction.CREATE, EntityType.SYSTEM, null,
                String.format("Broadcast SMS to %d tenants (%d sent, %d failed)", tenants.size(), sent, failed));

        Map<String, Object> result = Map.of(
                "totalTenants", tenants.size(),
                "sent", sent,
                "failed", failed
        );

        return ResponseEntity.ok(ApiResponse.success(
                String.format("Broadcast complete: %d sent, %d failed", sent, failed), result));
    }

    @PostMapping("/send-to-tenant/{tenantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Send SMS to a specific tenant by ID")
    public ResponseEntity<ApiResponse<String>> sendToTenant(
            @PathVariable Long tenantId,
            @RequestBody Map<String, String> payload) {
        String message = payload.get("message");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Message is required"));
        }

        User tenant = userRepository.findById(tenantId)
                .orElse(null);

        if (tenant == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Tenant not found"));
        }

        if (tenant.getPhoneNumber() == null || tenant.getPhoneNumber().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Tenant has no registered phone number"));
        }

        smsService.sendSms(tenant.getPhoneNumber(), message);
        auditLogService.log(AuditAction.CREATE, EntityType.SYSTEM, tenantId,
                "SMS sent to tenant: " + tenant.getEmail());

        return ResponseEntity.ok(ApiResponse.success(
                "SMS sent to " + tenant.getFirstName() + " (" + tenant.getPhoneNumber() + ")", null));
    }
}

