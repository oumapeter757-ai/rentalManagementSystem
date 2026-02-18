package com.peterscode.rentalmanagementsystem.service.sms;

import com.peterscode.rentalmanagementsystem.config.TwilioConfig;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final TwilioConfig twilioConfig;

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
        }
    }
}
