package com.peterscode.rentalmanagementsystem.service.email;

import com.peterscode.rentalmanagementsystem.exception.AuthenticationFailedException;
import com.peterscode.rentalmanagementsystem.model.logs.EmailLog;
import com.peterscode.rentalmanagementsystem.repository.EmailLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailLogRepository emailLogRepository;


    @Override
    @Async
    public void sendEmail(String recipientEmail, String subject, String body) {
        EmailLog logEntry = EmailLog.builder()
                .recipientEmail(recipientEmail)
                .subject(subject)
                .body(body)
                .sent(false)
                .build();

        if (!isValidGoogleEmail(recipientEmail)) {
            String error = "Invalid email. Only Gmail addresses allowed. Provided: " + recipientEmail;
            log.error(error);
            logEntry.setErrorMessage(error);
            emailLogRepository.save(logEntry);
            throw new AuthenticationFailedException(error);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(body, true); // allows HTML

            mailSender.send(message);

            logEntry.setSent(true);
            log.info("Email sent successfully to {}", recipientEmail);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", recipientEmail, e.getMessage());
            logEntry.setSent(false);
            logEntry.setErrorMessage(e.getMessage());
            throw new AuthenticationFailedException("Email sending failed: " + e.getMessage());
        } finally {
            emailLogRepository.save(logEntry);
        }
    }

    /**
     * Validates that the email belongs to Gmail.
     */
    private boolean isValidGoogleEmail(String email) {
        return email != null && email.toLowerCase().endsWith("@gmail.com");
    }
}
