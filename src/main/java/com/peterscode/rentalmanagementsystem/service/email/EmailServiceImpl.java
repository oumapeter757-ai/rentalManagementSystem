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

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    @Override
    @Transactional
    public EmailLogResponse sendEmail(EmailRequest emailRequest) {
        log.info("Sending email to: {}", emailRequest.getRecipient());

        EmailLog emailLog = createEmailLog(emailRequest);

        try {
            // Generate HTML content based on template
            String htmlContent = generateHtmlContent(emailRequest);
            emailRequest.setBody(htmlContent); // Update request with generated content

            // Check if email sending is configured
            if (isEmailConfigured()) {
                // Create and send real email
                sendRealEmail(emailRequest);
                emailLog.setStatus(EmailStatus.SENT);
                log.info("✅ Email sent successfully to: {}", emailRequest.getRecipient());

                // Log password reset code if applicable
                if (emailRequest.getTemplateName() != null &&
                        emailRequest.getTemplateName().contains("password-reset-code")) {
                    Map<String, Object> variables = emailRequest.getVariables();
                    if (variables != null && variables.containsKey("resetCode")) {
                        String resetCode = (String) variables.get("resetCode");
                        log.info("🔐 Password Reset Code for {}: {}", emailRequest.getRecipient(), resetCode);
                        log.info("⏰ Code expires in: {} minutes", variables.getOrDefault("expiryMinutes", 5));
                    }
                }
            } else {
                // Log email details only (development mode)
                logEmailDetails(emailRequest);
                emailLog.setStatus(EmailStatus.SENT);
                log.info("📧 Email logged (development mode) to: {}", emailRequest.getRecipient());

                // For password reset code emails, show the code in logs
                if (emailRequest.getTemplateName() != null &&
                        emailRequest.getTemplateName().contains("password-reset-code")) {
                    logPasswordResetCodeInfo(emailRequest);
                }
            }

            emailLog.setSentAt(LocalDateTime.now());
            emailLog.setBody(htmlContent);

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

        helper.setText(emailRequest.getBody(), true);

        javaMailSender.send(message);
    }

    private String generateHtmlContent(EmailRequest emailRequest) {
        Map<String, Object> variables = emailRequest.getVariables() != null
                ? emailRequest.getVariables()
                : new HashMap<>();

        String firstName = (String) variables.getOrDefault("firstName", "User");
        int currentYear = LocalDateTime.now().getYear();

        // Check template name safely
        String templateName = emailRequest.getTemplateName();

        if (templateName != null && templateName.contains("verification")) {
            String verificationLink = (String) variables.getOrDefault("verificationLink", "");
            Integer expiryHours = (Integer) variables.getOrDefault("expiryHours", 24);
            return generateVerificationEmailHtml(firstName, verificationLink, expiryHours, currentYear);
        }

        if (templateName != null && templateName.contains("password-reset")) {
            String token = (String) variables.getOrDefault("token", "");
            Integer expiryHours = (Integer) variables.getOrDefault("expiryHours", 24);
            String frontendResetLink = frontendUrl + "/auth/reset-password?token=" + token;
            String directApiLink = backendUrl + "/api/auth/reset-password?token=" + token;

            return generatePasswordResetEmailHtml(firstName, token, frontendResetLink, directApiLink, expiryHours, currentYear);
        }

        if (templateName != null && templateName.contains("password-reset-code")) {
            String resetCode = (String) variables.getOrDefault("resetCode", "");
            Integer expiryMinutes = (Integer) variables.getOrDefault("expiryMinutes", 5);

            return generatePasswordResetCodeEmailHtml(firstName, resetCode, expiryMinutes, currentYear);
        }

        // Return custom body or default
        return emailRequest.getBody() != null ? emailRequest.getBody() : "";
    }

    private String generateVerificationEmailHtml(String firstName, String verificationLink, int expiryHours, int currentYear) {
        return String.format("""
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
            """, firstName, verificationLink, expiryHours, currentYear);
    }

    private String generatePasswordResetEmailHtml(String firstName, String token,
                                                  String frontendResetLink, String directApiLink,
                                                  int expiryHours, int currentYear) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Reset Your Password</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        margin: 0;
                        padding: 20px;
                        background-color: #f5f7fa;
                    }
                    .container {
                        max-width: 600px;
                        margin: 0 auto;
                        background-color: white;
                        border-radius: 12px;
                        padding: 30px;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.1);
                    }
                    .button {
                        display: inline-block;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        text-decoration: none;
                        padding: 14px 32px;
                        border-radius: 8px;
                        font-weight: 600;
                        font-size: 16px;
                        margin: 20px 0;
                        text-align: center;
                    }
                    .token-box {
                        background-color: #f8f9fa;
                        border: 2px dashed #dee2e6;
                        border-radius: 8px;
                        padding: 15px;
                        margin: 20px 0;
                        font-family: 'Courier New', monospace;
                        font-size: 14px;
                        word-break: break-all;
                    }
                    .footer {
                        margin-top: 30px;
                        padding-top: 20px;
                        border-top: 1px solid #eee;
                        font-size: 12px;
                        color: #666;
                        text-align: center;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2>Reset Your Password</h2>
                    <p>Hello <strong>%s</strong>,</p>
                    <p>We received a request to reset your password for your Rental Management System account.</p>
                    
                    <div style="text-align: center; margin: 25px 0;">
                        <a href="%s" class="button">Click Here to Reset Password</a>
                        <p style="font-size: 12px; color: #666; margin-top: 10px;">
                            (Click the button above to reset your password)
                        </p>
                    </div>
                    
                    <div class="token-box">
                        <strong>Your Reset Token:</strong><br>
                        <code>%s</code>
                    </div>
                    
                    <p><strong>Alternative Method:</strong> If the button doesn't work, you can:</p>
                    <ol>
                        <li>Go to: %s/auth/reset-password</li>
                        <li>Enter this token: <code>%s</code></li>
                    </ol>
                    
                    <p><strong>Or use this direct API link:</strong></p>
                    <div class="token-box">
                        <code>%s</code>
                    </div>
                    
                    <p><strong>Important:</strong></p>
                    <ul>
                        <li>This token expires in <strong>%d hours</strong></li>
                        <li>Never share this token with anyone</li>
                        <li>If you didn't request this, please ignore this email</li>
                    </ul>
                    
                    <div class="footer">
                        <p>© %d Rental Management System</p>
                        <p>This is an automated message. Please do not reply.</p>
                    </div>
                </div>
            </body>
            </html>
            """, firstName, frontendResetLink, token, frontendUrl, token, directApiLink, expiryHours, currentYear);
    }

    private String generatePasswordResetCodeEmailHtml(String firstName, String resetCode,
                                                      int expiryMinutes, int currentYear) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; padding: 30px; }
                    .code { font-size: 48px; font-weight: bold; letter-spacing: 10px; color: #4F46E5; margin: 20px 0; text-align: center; }
                    .expiry { color: #666; font-size: 14px; margin: 10px 0; text-align: center; }
                    .warning { background: #fff3cd; border: 1px solid #ffecb5; padding: 15px; margin: 20px 0; border-radius: 5px; }
                    .footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; font-size: 12px; color: #666; text-align: center; }
                    .header { text-align: center; margin-bottom: 20px; }
                    .header h2 { color: #4F46E5; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>Password Reset Code</h2>
                    </div>
                    <p>Hello <strong>%s</strong>,</p>
                    <p>You requested to reset your password. Use the verification code below to complete the reset process.</p>
                    
                    <div class="code">%s</div>
                    <div class="expiry">⏰ This code expires in: %d minutes</div>
                    
                    <div class="warning">
                        <strong>Security Notice:</strong>
                        <ul>
                            <li>Do not share this code with anyone</li>
                            <li>This code expires in %d minutes</li>
                            <li>Use it immediately on the password reset page</li>
                            <li>If you didn't request this, please ignore this email</li>
                        </ul>
                    </div>
                    
                    <p><strong>How to use:</strong></p>
                    <ol>
                        <li>Go to: %s/auth/reset-password</li>
                        <li>Enter the 6-digit code: <strong>%s</strong></li>
                        <li>Create your new password</li>
                    </ol>
                    
                    <div class="footer">
                        <p>© %d Rental Management System</p>
                        <p>This is an automated message. Please do not reply.</p>
                    </div>
                </div>
            </body>
            </html>
            """, firstName, resetCode, expiryMinutes, expiryMinutes, frontendUrl, resetCode, currentYear);
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

    private void logPasswordResetCodeInfo(EmailRequest emailRequest) {
        Map<String, Object> variables = emailRequest.getVariables();
        if (variables != null && variables.containsKey("resetCode")) {
            String resetCode = (String) variables.get("resetCode");
            Integer expiryMinutes = (Integer) variables.getOrDefault("expiryMinutes", 5);

            log.info("   🔐 Password Reset Code: {}", resetCode);
            log.info("   ⏰ Expires in: {} minutes", expiryMinutes);
            log.info("   🌐 Reset URL: {}/auth/reset-password", frontendUrl);

            log.info("   💡 To test with curl:");
            log.info("       curl -X POST '{}/api/auth/reset-password' \\", backendUrl);
            log.info("            -H 'Content-Type: application/json' \\");
            log.info("            -d '{\"token\": \"%s\", \"newPassword\": \"NewPassword123!\", \"confirmPassword\": \"NewPassword123!\"}'", resetCode);
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

    private Map<String, Object> extractVariablesFromBody(String body) {
        Map<String, Object> variables = new HashMap<>();
        if (body == null || body.isEmpty()) return variables;

        // Extract reset code from HTML (for 6-digit codes)
        if (body.contains("class=\"code\"")) {
            int start = body.indexOf("class=\"code\"");
            if (start > 0) {
                start = body.indexOf(">", start) + 1;
                int end = body.indexOf("</div>", start);
                if (start > 0 && end > start) {
                    String code = body.substring(start, end).trim();
                    variables.put("resetCode", code);
                }
            }
        }

        // Extract token from HTML (for UUID tokens)
        if (body.contains("Your Reset Token:")) {
            int start = body.indexOf("<code>") + 6;
            int end = body.indexOf("</code>", start);
            if (start > 5 && end > start) {
                String token = body.substring(start, end).trim();
                variables.put("token", token);
            }
        }

        // Extract first name
        if (body.contains("Hello <strong>")) {
            int start = body.indexOf("Hello <strong>") + 14;
            int end = body.indexOf("</strong>", start);
            if (start > 13 && end > start) {
                String firstName = body.substring(start, end).trim();
                variables.put("firstName", firstName);
            }
        }

        return variables;
    }
}