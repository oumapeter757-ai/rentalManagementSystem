
package com.peterscode.rentalmanagementsystem.service.email;

import com.peterscode.rentalmanagementsystem.dto.request.EmailBatchRequest;
import com.peterscode.rentalmanagementsystem.dto.request.EmailRequest;
import com.peterscode.rentalmanagementsystem.dto.request.ResendEmailRequest;
import com.peterscode.rentalmanagementsystem.dto.response.EmailLogResponse;
import com.peterscode.rentalmanagementsystem.dto.response.EmailStatisticsResponse;

import com.peterscode.rentalmanagementsystem.model.logs.EmailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EmailService {

    // Send emails
    EmailLogResponse sendEmail(EmailRequest emailRequest);

    void sendEmailAsync(EmailRequest emailRequest);

    void sendBatchEmails(EmailBatchRequest batchRequest);

    // Retry failed emails
    EmailLogResponse resendEmail(ResendEmailRequest resendRequest);

    void retryFailedEmails();

    // Retrieve emails
    Page<EmailLogResponse> getAllEmailLogs(Pageable pageable);

    Page<EmailLogResponse> getEmailLogsByStatus(EmailStatus status, Pageable pageable);

    Page<EmailLogResponse> getEmailLogsByRecipient(String recipient, Pageable pageable);

    Page<EmailLogResponse> searchEmailLogs(String searchTerm, Pageable pageable);

    EmailLogResponse getEmailLogById(Long id);

    // Statistics
    EmailStatisticsResponse getEmailStatistics();

    // Update email status (for webhooks)
    void updateEmailStatus(Long emailLogId, EmailStatus status, String errorMessage);

    void markAsDelivered(Long emailLogId);

    void markAsOpened(Long emailLogId);

    // Cleanup
    void cleanupOldLogs(int daysToKeep);
}