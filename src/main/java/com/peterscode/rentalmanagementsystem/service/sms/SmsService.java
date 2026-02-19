package com.peterscode.rentalmanagementsystem.service.sms;

import com.peterscode.rentalmanagementsystem.config.TwilioConfig;
import com.peterscode.rentalmanagementsystem.model.sms.ReminderType;
import com.peterscode.rentalmanagementsystem.model.sms.SmsReminder;
import com.peterscode.rentalmanagementsystem.model.sms.SmsStatus;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.SmsReminderRepository;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final TwilioConfig twilioConfig;
    private final SmsReminderRepository smsReminderRepository;

    /**
     * Send SMS and log the attempt
     */
    @Transactional
    public void sendSms(String to, String message) {
        try {
            if (twilioConfig.getPhoneNumber() == null || twilioConfig.getPhoneNumber().isEmpty()) {
                log.warn("Twilio phone number not configured. Skipping SMS to {}", to);
                return;
            }

            Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(twilioConfig.getPhoneNumber()),
                    message).create();

            log.info("SMS sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send SMS", e);
        }
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
