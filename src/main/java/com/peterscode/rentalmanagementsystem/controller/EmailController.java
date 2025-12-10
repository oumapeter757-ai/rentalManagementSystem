
package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.request.EmailBatchRequest;
import com.peterscode.rentalmanagementsystem.dto.request.EmailRequest;
import com.peterscode.rentalmanagementsystem.dto.request.ResendEmailRequest;
import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.dto.response.EmailLogResponse;
import com.peterscode.rentalmanagementsystem.dto.response.EmailStatisticsResponse;

import com.peterscode.rentalmanagementsystem.model.logs.EmailStatus;
import com.peterscode.rentalmanagementsystem.service.email.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/emails")
@RequiredArgsConstructor
@Tag(name = "Email Management", description = "APIs for managing email logs and sending emails")
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/send")
    @Operation(summary = "Send a single email")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'LANDLORD')")
    public ResponseEntity<ApiResponse<EmailLogResponse>> sendEmail(@Valid @RequestBody EmailRequest emailRequest) {
        EmailLogResponse response = emailService.sendEmail(emailRequest);
        return ResponseEntity.ok(ApiResponse.success("Email sent successfully", response));
    }

    @PostMapping("/send-batch")
    @Operation(summary = "Send emails to multiple recipients")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> sendBatchEmails(@Valid @RequestBody EmailBatchRequest batchRequest) {
        emailService.sendBatchEmails(batchRequest);
        return ResponseEntity.ok(ApiResponse.success("Batch email sending initiated", null));
    }

    @PostMapping("/resend")
    @Operation(summary = "Resend a failed email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EmailLogResponse>> resendEmail(@Valid @RequestBody ResendEmailRequest resendRequest) {
        EmailLogResponse response = emailService.resendEmail(resendRequest);
        return ResponseEntity.ok(ApiResponse.success("Email resent successfully", response));
    }

    @PostMapping("/retry-failed")
    @Operation(summary = "Retry all failed emails")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> retryFailedEmails() {
        emailService.retryFailedEmails();
        return ResponseEntity.ok(ApiResponse.success("Retry process initiated for failed emails", null));
    }

    @GetMapping
    @Operation(summary = "Get all email logs with pagination")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<EmailLogResponse>>> getAllEmailLogs(
            @PageableDefault(size = 20, sort = "lastAttemptAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<EmailLogResponse> response = emailService.getAllEmailLogs(pageable);
        return ResponseEntity.ok(ApiResponse.success("Email logs retrieved successfully", response));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get email logs by status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<EmailLogResponse>>> getEmailLogsByStatus(
            @PathVariable EmailStatus status,
            @PageableDefault(size = 20, sort = "lastAttemptAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<EmailLogResponse> response = emailService.getEmailLogsByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Email logs retrieved successfully", response));
    }

    @GetMapping("/recipient/{recipient}")
    @Operation(summary = "Get email logs by recipient")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<EmailLogResponse>>> getEmailLogsByRecipient(
            @PathVariable String recipient,
            @PageableDefault(size = 20, sort = "lastAttemptAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<EmailLogResponse> response = emailService.getEmailLogsByRecipient(recipient, pageable);
        return ResponseEntity.ok(ApiResponse.success("Email logs retrieved successfully", response));
    }

    @GetMapping("/search")
    @Operation(summary = "Search email logs by recipient or subject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<EmailLogResponse>>> searchEmailLogs(
            @RequestParam String query,
            @PageableDefault(size = 20, sort = "lastAttemptAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<EmailLogResponse> response = emailService.searchEmailLogs(query, pageable);
        return ResponseEntity.ok(ApiResponse.success("Email logs retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get email log by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<EmailLogResponse>> getEmailLogById(@PathVariable Long id) {
        EmailLogResponse response = emailService.getEmailLogById(id);
        return ResponseEntity.ok(ApiResponse.success("Email log retrieved successfully", response));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get email statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<EmailStatisticsResponse>> getEmailStatistics() {
        EmailStatisticsResponse response = emailService.getEmailStatistics();
        return ResponseEntity.ok(ApiResponse.success("Email statistics retrieved successfully", response));
    }

    @PutMapping("/{id}/mark-delivered")
    @Operation(summary = "Mark email as delivered (webhook)")
    public ResponseEntity<ApiResponse<Void>> markAsDelivered(@PathVariable Long id) {
        emailService.markAsDelivered(id);
        return ResponseEntity.ok(ApiResponse.success("Email marked as delivered", null));
    }

    @PutMapping("/{id}/mark-opened")
    @Operation(summary = "Mark email as opened (webhook)")
    public ResponseEntity<ApiResponse<Void>> markAsOpened(@PathVariable Long id) {
        emailService.markAsOpened(id);
        return ResponseEntity.ok(ApiResponse.success("Email marked as opened", null));
    }

    @DeleteMapping("/cleanup")
    @Operation(summary = "Cleanup old email logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> cleanupOldLogs(@RequestParam(defaultValue = "30") int daysToKeep) {
        emailService.cleanupOldLogs(daysToKeep);
        return ResponseEntity.ok(ApiResponse.success("Old email logs cleaned up successfully", null));
    }
}