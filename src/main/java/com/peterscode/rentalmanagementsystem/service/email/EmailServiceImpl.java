package com.peterscode.rentalmanagementsystem.service.email;

import com.peterscode.rentalmanagementsystem.dto.request.EmailBatchRequest;
import com.peterscode.rentalmanagementsystem.dto.request.EmailRequest;
import com.peterscode.rentalmanagementsystem.dto.request.ResendEmailRequest;
import com.peterscode.rentalmanagementsystem.dto.response.EmailLogResponse;
import com.peterscode.rentalmanagementsystem.dto.response.EmailStatisticsResponse;
import com.peterscode.rentalmanagementsystem.exception.ResourceNotFoundException;


import com.peterscode.rentalmanagementsystem.model.logs.EmailLog;
import com.peterscode.rentalmanagementsystem.model.logs.EmailStatus;
import com.peterscode.rentalmanagementsystem.repository.EmailLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final EmailLogRepository emailLogRepository;

    @Override
    @Transactional
    public EmailLogResponse sendEmail(EmailRequest emailRequest) {
        log.info("Sending email to: {}", emailRequest.getRecipient());

        EmailLog emailLog = createEmailLog(emailRequest);

        try {
            // Log email details instead of actually sending
            log.info("📧 Email would be sent:");
            log.info("   To: {}", emailRequest.getRecipient());
            log.info("   Subject: {}", emailRequest.getSubject());
            log.info("   Template: {}", emailRequest.getTemplateName());

            // Mark as sent (logging only)
            emailLog.setStatus(EmailStatus.SENT);
            emailLog.setSentAt(LocalDateTime.now());

        } catch (Exception e) {
            handleEmailError(emailLog, e);
            log.error("Failed to process email for {}: {}",
                    emailRequest.getRecipient(), e.getMessage());
        }

        emailLog = emailLogRepository.save(emailLog);
        return mapToResponse(emailLog);
    }

    @Override
    @Async("emailTaskExecutor")
    @Transactional
    public void sendEmailAsync(EmailRequest emailRequest) {
        CompletableFuture.runAsync(() -> sendEmail(emailRequest));
    }

    @Override
    @Async("emailTaskExecutor")
    public void sendBatchEmails(EmailBatchRequest batchRequest) {
        log.info("Sending batch emails to {} recipients", batchRequest.getRecipients().size());

        batchRequest.getRecipients().forEach(recipient -> {
            EmailRequest emailRequest = EmailRequest.builder()
                    .recipient(recipient)
                    .subject(batchRequest.getSubject())
                    .body(batchRequest.getBody())
                    .templateName(batchRequest.getTemplateName())
                    .build();
            sendEmailAsync(emailRequest);
        });
    }

    @Override
    @Transactional
    public EmailLogResponse resendEmail(ResendEmailRequest resendRequest) {
        log.info("Resending email with ID: {}", resendRequest.getEmailLogId());

        EmailLog emailLog = emailLogRepository.findById(resendRequest.getEmailLogId())
                .orElseThrow(() -> new ResourceNotFoundException("EmailLog not found with id: " + resendRequest.getEmailLogId()));

        // Increment retry count
        emailLog.setRetryCount(emailLog.getRetryCount() + 1);
        emailLog.setLastAttemptAt(LocalDateTime.now());

        EmailRequest emailRequest = EmailRequest.builder()
                .recipient(emailLog.getRecipient())
                .subject(emailLog.getSubject())
                .body(emailLog.getBody())
                .templateName(emailLog.getTemplateName())
                .build();

        log.info("Resending email to: {}", emailLog.getRecipient());

        emailLog = emailLogRepository.save(emailLog);
        return mapToResponse(emailLog);
    }

    @Override
    @Transactional
    public void retryFailedEmails() {
        log.info("Retrying failed emails");

        List<EmailLog> failedEmails = emailLogRepository
                .findByStatusAndLastAttemptAtBefore(EmailStatus.FAILED,
                        LocalDateTime.now().minusHours(1));

        failedEmails.forEach(emailLog -> {
            if (emailLog.getRetryCount() < 3) { // Max retries
                ResendEmailRequest request = new ResendEmailRequest(emailLog.getId());
                try {
                    resendEmail(request);
                } catch (Exception e) {
                    log.error("Failed to retry email id {}: {}", emailLog.getId(), e.getMessage());
                }
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmailLogResponse> getAllEmailLogs(Pageable pageable) {
        return emailLogRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmailLogResponse> getEmailLogsByStatus(EmailStatus status, Pageable pageable) {
        return emailLogRepository.findByStatus(status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmailLogResponse> getEmailLogsByRecipient(String recipient, Pageable pageable) {
        return emailLogRepository.findByRecipient(recipient, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmailLogResponse> searchEmailLogs(String searchTerm, Pageable pageable) {
        return emailLogRepository.searchByRecipientOrSubject(searchTerm, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public EmailLogResponse getEmailLogById(Long id) {
        EmailLog emailLog = emailLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmailLog not found with id: " + id));
        return mapToResponse(emailLog);
    }

    @Override
    @Transactional(readOnly = true)
    public EmailStatisticsResponse getEmailStatistics() {
        long total = emailLogRepository.count();
        long sent = emailLogRepository.countByStatus(EmailStatus.SENT);
        long failed = emailLogRepository.countByStatus(EmailStatus.FAILED);
        long pending = emailLogRepository.countByStatus(EmailStatus.PENDING);
        long delivered = emailLogRepository.countByStatus(EmailStatus.DELIVERED);
        long opened = emailLogRepository.countByStatus(EmailStatus.OPENED);

        long last24Hours = emailLogRepository.countEmailsSentBetween(
                LocalDateTime.now().minusDays(1), LocalDateTime.now());
        long last7Days = emailLogRepository.countEmailsSentBetween(
                LocalDateTime.now().minusDays(7), LocalDateTime.now());

        double successRate = total > 0 ? ((double) sent / total) * 100 : 0;

        return EmailStatisticsResponse.builder()
                .totalEmails(total)
                .sentCount(sent)
                .failedCount(failed)
                .pendingCount(pending)
                .deliveredCount(delivered)
                .openedCount(opened)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .last24Hours(last24Hours)
                .last7Days(last7Days)
                .build();
    }

    @Override
    @Transactional
    public void updateEmailStatus(Long emailLogId, EmailStatus status, String errorMessage) {
        EmailLog emailLog = emailLogRepository.findById(emailLogId)
                .orElseThrow(() -> new ResourceNotFoundException("EmailLog not found with id: " + emailLogId));

        emailLog.setStatus(status);
        if (errorMessage != null) {
            emailLog.setErrorMessage(errorMessage);
        }
        if (status == EmailStatus.DELIVERED) {
            emailLog.setSentAt(LocalDateTime.now());
        }

        emailLogRepository.save(emailLog);
        log.info("Updated email id {} status to {}", emailLogId, status);
    }

    @Override
    @Transactional
    public void markAsDelivered(Long emailLogId) {
        updateEmailStatus(emailLogId, EmailStatus.DELIVERED, null);
    }

    @Override
    @Transactional
    public void markAsOpened(Long emailLogId) {
        updateEmailStatus(emailLogId, EmailStatus.OPENED, null);
    }

    @Override
    @Transactional
    public void cleanupOldLogs(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        List<EmailLog> oldLogs = emailLogRepository.findAll().stream()
                .filter(log -> log.getLastAttemptAt() != null && log.getLastAttemptAt().isBefore(cutoffDate))
                .toList();

        emailLogRepository.deleteAll(oldLogs);
        log.info("Cleaned up {} old email logs older than {} days", oldLogs.size(), daysToKeep);
    }

    // Helper methods
    private EmailLog createEmailLog(EmailRequest emailRequest) {
        return EmailLog.builder()
                .recipient(emailRequest.getRecipient())
                .subject(emailRequest.getSubject())
                .body(emailRequest.getBody())
                .templateName(emailRequest.getTemplateName())
                .status(EmailStatus.PENDING)
                .retryCount(0)
                .lastAttemptAt(LocalDateTime.now())
                .build();
    }

    private void handleEmailError(EmailLog emailLog, Exception e) {
        emailLog.setStatus(EmailStatus.FAILED);
        emailLog.setErrorMessage("Logging only - Email service not configured: " + e.getMessage());
    }

    private EmailLogResponse mapToResponse(EmailLog emailLog) {
        String bodyPreview = emailLog.getBody() != null && emailLog.getBody().length() > 100
                ? emailLog.getBody().substring(0, 100) + "..."
                : emailLog.getBody();

        return EmailLogResponse.builder()
                .id(emailLog.getId())
                .recipient(emailLog.getRecipient())
                .subject(emailLog.getSubject())
                .bodyPreview(bodyPreview)
                .templateName(emailLog.getTemplateName())
                .status(emailLog.getStatus())
                .retryCount(emailLog.getRetryCount())
                .sentAt(emailLog.getSentAt())
                .lastAttemptAt(emailLog.getLastAttemptAt())
                .errorMessage(emailLog.getErrorMessage())
                .build();
    }
}