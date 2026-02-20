package com.peterscode.rentalmanagementsystem.service.sms;

import com.peterscode.rentalmanagementsystem.config.AfricasTalkingConfig;
import com.peterscode.rentalmanagementsystem.model.sms.ReminderType;
import com.peterscode.rentalmanagementsystem.model.sms.SmsReminder;
import com.peterscode.rentalmanagementsystem.model.sms.SmsStatus;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.SmsReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final AfricasTalkingConfig africasTalkingConfig;
    private final SmsReminderRepository smsReminderRepository;

    /**
     * Send SMS via Africa's Talking
     */
    @Transactional
    public void sendSms(String to, String message) {
        String normalizedTo = normalizePhoneNumber(to);
        log.info("Sending SMS to {} (normalized: {})", to, normalizedTo);

        if (!africasTalkingConfig.isConfigured()) {
            log.error("Africa's Talking is not configured. Cannot send SMS to {}", normalizedTo);
            throw new RuntimeException("SMS provider not configured");
        }

        try {
            String senderId = africasTalkingConfig.getSenderId();
            String from = (senderId != null && !senderId.isEmpty()) ? senderId : null;

            String result = africasTalkingConfig.sendSms(
                    message, from, Arrays.asList(normalizedTo));
            log.info("SMS sent via Africa's Talking to {}: {}", normalizedTo, result);
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", normalizedTo, e.getMessage(), e);
            throw new RuntimeException("Failed to send SMS: " + e.getMessage(), e);
        }
    }

    /**
     * Normalize phone number to E.164 format for Kenya (+254...)
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number is empty");
        }
        String cleaned = phone.trim().replaceAll("[\\s\\-()]+", "");
        if (cleaned.startsWith("+")) {
            return cleaned;
        }
        if (cleaned.startsWith("0")) {
            return "+254" + cleaned.substring(1);
        }
        if (cleaned.length() == 9) {
            return "+254" + cleaned;
        }
        if (cleaned.startsWith("254") && cleaned.length() >= 12) {
            return "+" + cleaned;
        }
        return "+254" + cleaned;
    }

    /**
     * Send SMS reminder to a tenant and track it
     */
    @Transactional
    public void sendReminder(User tenant, String message, ReminderType reminderType) {
        SmsReminder reminder = SmsReminder.builder()
                .tenant(tenant)
                .phoneNumber(tenant.getPhoneNumber())
                .message(message)
                .reminderType(reminderType)
                .status(SmsStatus.PENDING)
                .build();

        try {
            if (tenant.getPhoneNumber() == null || tenant.getPhoneNumber().isEmpty()) {
                log.warn("Tenant {} has no phone number. Cannot send SMS reminder.", tenant.getEmail());
                reminder.setStatus(SmsStatus.FAILED);
                reminder.setErrorMessage("No phone number registered");
                smsReminderRepository.save(reminder);
                return;
            }

            sendSms(tenant.getPhoneNumber(), message);
            reminder.setStatus(SmsStatus.SENT);
            reminder.setSentAt(LocalDateTime.now());
            log.info("SMS reminder sent to tenant {}: {}", tenant.getEmail(), reminderType);

        } catch (Exception e) {
            reminder.setStatus(SmsStatus.FAILED);
            reminder.setErrorMessage(e.getMessage());
            log.error("Failed to send SMS reminder to tenant {}: {}", tenant.getEmail(), e.getMessage());
        } finally {
            smsReminderRepository.save(reminder);
        }
    }

    /**
     * Send payment reminder
     */
    public void sendPaymentReminder(User tenant, String propertyTitle, String amount, LocalDateTime dueDate) {
        String message = String.format(
            "Dear %s, your rent payment of KSh %s for %s is due on %s. Please complete your payment to avoid late fees. Thank you.",
            tenant.getFirstName() != null ? tenant.getFirstName() : "Tenant",
            amount,
            propertyTitle,
            dueDate.toLocalDate()
        );
        sendReminder(tenant, message, ReminderType.PAYMENT_DUE);
    }

    /**
     * Send balance reminder
     */
    public void sendBalanceReminder(User tenant, String balance) {
        String message = String.format(
            "Dear %s, you have an outstanding balance of KSh %s. Please settle your payment within 15 days. Thank you.",
            tenant.getFirstName() != null ? tenant.getFirstName() : "Tenant",
            balance
        );
        sendReminder(tenant, message, ReminderType.BALANCE_REMINDER);
    }

    /**
     * Send deposit reminder
     */
    public void sendDepositReminder(User tenant, String propertyTitle, String depositAmount) {
        String message = String.format(
            "Dear %s, please complete your deposit payment of KSh %s for %s to secure your booking. Thank you.",
            tenant.getFirstName() != null ? tenant.getFirstName() : "Tenant",
            depositAmount,
            propertyTitle
        );
        sendReminder(tenant, message, ReminderType.DEPOSIT_REMINDER);
    }
}
