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
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final EmailLogRepository emailLogRepository;
    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username:noreply@rentalmanagementsystem.com}")
    private String fromEmail;

    @Value("${app.verification-url:http://localhost:8080/api/auth}")
    private String verificationBaseUrl;



    @Override
    @Transactional
    public EmailLogResponse sendEmail(EmailRequest emailRequest) {
        log.info("Sending email to: {}", emailRequest.getRecipient());

        EmailLog emailLog = createEmailLog(emailRequest);

        try {
            // Check if email sending is configured
            if (isEmailConfigured()) {
                // Create and send real email
                sendRealEmail(emailRequest);
                emailLog.setStatus(EmailStatus.SENT);
                log.info("✅ Email sent successfully to: {}", emailRequest.getRecipient());
            } else {
                // Log email details only (development mode)
                logEmailDetails(emailRequest);
                emailLog.setStatus(EmailStatus.SENT);
                log.info("📧 Email logged (development mode) to: {}", emailRequest.getRecipient());

                // For verification emails, show the verification link in logs
                if (emailRequest.getTemplateName() != null &&
                        emailRequest.getTemplateName().contains("verification")) {
                    logVerificationLink(emailRequest);
                }
            }

            emailLog.setSentAt(LocalDateTime.now());

        } catch (Exception e) {
            handleEmailError(emailLog, e);
            log.error("❌ Failed to process email for {}: {}",
                    emailRequest.getRecipient(), e.getMessage());
        }

        emailLog = emailLogRepository.save(emailLog);
        return mapToResponse(emailLog);
    }

    @Override
    @Async("emailTaskExecutor")
    @Transactional
    public void sendEmailAsync(EmailRequest emailRequest) {
        CompletableFuture.runAsync(() -> {
            try {
                sendEmail(emailRequest);
                log.debug("Async email sent successfully to: {}", emailRequest.getRecipient());
            } catch (Exception e) {
                log.error("Async email sending failed for {}: {}",
                        emailRequest.getRecipient(), e.getMessage());
            }
        });
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
                    .variables(batchRequest.getVariables())
                    .html(batchRequest.isHtml())
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
                .variables(extractVariablesFromBody(emailLog.getBody()))
                .html(true)
                .build();

        log.info("Resending email to: {}", emailLog.getRecipient());

        EmailLogResponse response = sendEmail(emailRequest);

        // Update the original log with new status
        emailLog.setStatus(response.getStatus());
        emailLog.setSentAt(response.getSentAt());
        emailLog.setErrorMessage(response.getErrorMessage());
        emailLogRepository.save(emailLog);

        return response;
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

    // ========== PRIVATE HELPER METHODS ==========

    private boolean isEmailConfigured() {
        return javaMailSender != null &&
                fromEmail != null &&
                !fromEmail.equals("noreply@rentalmanagementsystem.com") &&
                !fromEmail.contains("localhost") &&
                !fromEmail.contains("example.com");
    }

    private void sendRealEmail(EmailRequest emailRequest) throws Exception {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(emailRequest.getRecipient());
        helper.setSubject(emailRequest.getSubject());

        // Generate HTML content
        String htmlContent = generateHtmlContent(emailRequest);
        helper.setText(htmlContent, true);

        javaMailSender.send(message);
    }

    private String generateHtmlContent(EmailRequest emailRequest) {
        Map<String, Object> variables = emailRequest.getVariables() != null
                ? emailRequest.getVariables()
                : new HashMap<>();

        String firstName = (String) variables.getOrDefault("firstName", "User");
        String verificationLink = (String) variables.getOrDefault("verificationLink", "");
        Integer expiryHours = (Integer) variables.getOrDefault("expiryHours", 24);
        int currentYear = LocalDateTime.now().getYear();

        // Check template name safely
        String templateName = emailRequest.getTemplateName();

        if (templateName != null && templateName.contains("verification")) {
            return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .button { 
                            background-color: #4F46E5; 
                            color: white; 
                            padding: 12px 24px; 
                            text-decoration: none; 
                            border-radius: 4px; 
                            display: inline-block; 
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h2>Verify Your Email</h2>
                        <p>Hello %s,</p>
                        <p>Please verify your email by clicking the button below:</p>
                        <a href="%s" class="button">Verify Email</a>
                        <p>This link expires in %d hours.</p>
                        <p>If you didn't create an account, please ignore this email.</p>
                        <p>© %d Rental Management System</p>
                    </div>
                </body>
                </html>
                """.formatted(firstName, verificationLink, expiryHours, currentYear);
        }

        // Return custom body or default
        return emailRequest.getBody() != null ? emailRequest.getBody() : "";
    }

    private void logEmailDetails(EmailRequest emailRequest) {
        log.info("📧 Email Details:");
        log.info("   From: {}", fromEmail);
        log.info("   To: {}", emailRequest.getRecipient());
        log.info("   Subject: {}", emailRequest.getSubject());

        if (emailRequest.getTemplateName() != null) {
            log.info("   Template: {}", emailRequest.getTemplateName());
        }

        if (emailRequest.getVariables() != null && !emailRequest.getVariables().isEmpty()) {
            log.info("   Variables: {}", emailRequest.getVariables());
        }
    }

    private void logVerificationLink(EmailRequest emailRequest) {
        if (emailRequest.getVariables() != null && emailRequest.getVariables().containsKey("verificationLink")) {
            String verificationLink = (String) emailRequest.getVariables().get("verificationLink");
            log.info("   🔗 Verification Link: {}", verificationLink);

            // Extract token for manual verification
            String token = extractTokenFromVerificationLink(verificationLink);
            if (token != null) {
                String manualVerifyUrl = verificationBaseUrl + "/verify-email/" + token;
                log.info("   🛠️  Manual Verification URL: {}", manualVerifyUrl);
                log.info("   💡 To manually verify, run: curl -X POST {}", manualVerifyUrl);
            }
        }
    }

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
        emailLog.setErrorMessage(e.getMessage());
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

    private String extractTokenFromVerificationLink(String verificationLink) {
        if (verificationLink == null) return null;

        if (verificationLink.contains("token=")) {
            String tokenWithParams = verificationLink.substring(verificationLink.indexOf("token=") + 6);
            return tokenWithParams.split("&")[0];
        }

        if (verificationLink.contains("/verify-email/")) {
            return verificationLink.substring(verificationLink.lastIndexOf("/") + 1);
        }

        return null;
    }

    private Map<String, Object> extractVariablesFromBody(String body) {
        Map<String, Object> variables = new HashMap<>();
        if (body == null || body.isEmpty()) return variables;

        // Extract verification link from HTML
        if (body.contains("href=\"")) {
            int start = body.indexOf("href=\"") + 6;
            int end = body.indexOf("\"", start);
            if (start > 5 && end > start) {
                String link = body.substring(start, end);
                if (link.contains("verify-email")) {
                    variables.put("verificationLink", link);
                    String token = extractTokenFromVerificationLink(link);
                    if (token != null) {
                        variables.put("token", token);
                    }
                }
            }
        }

        // Extract first name
        if (body.contains("Hello ")) {
            int start = body.indexOf("Hello ") + 6;
            int end = body.indexOf(",", start);
            if (start > 5 && end > start) {
                String firstName = body.substring(start, end).trim();
                variables.put("firstName", firstName);
            }
        }

        return variables;
    }
}