package com.peterscode.rentalmanagementsystem.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peterscode.rentalmanagementsystem.config.MpesaConfig;
import com.peterscode.rentalmanagementsystem.dto.response.MpesaStkResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MpesaServiceImpl implements MpesaService {

    private final MpesaConfig mpesaConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String getAccessToken() {
        try {
            String auth = mpesaConfig.getConsumerKey() + ":" + mpesaConfig.getConsumerSecret();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encodedAuth);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    mpesaConfig.getAuthUrl() + "?grant_type=client_credentials",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            Map<String, Object> result = objectMapper.readValue(response.getBody(), Map.class);
            return (String) result.get("access_token");

        } catch (Exception e) {
            log.error("Error getting M-Pesa access token: {}", e.getMessage());
            throw new RuntimeException("Failed to get M-Pesa access token", e);
        }
    }

    @Override
    public MpesaStkResponse initiateStkPush(String phoneNumber, BigDecimal amount,
                                            String accountReference, String description) {
        try {
            String accessToken = getAccessToken();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            // Generate password
            String password = generatePassword(timestamp);

            // Prepare request body
            Map<String, Object> stkRequest = new HashMap<>();
            stkRequest.put("BusinessShortCode", mpesaConfig.getShortcode());
            stkRequest.put("Password", password);
            stkRequest.put("Timestamp", timestamp);
            stkRequest.put("TransactionType", "CustomerPayBillOnline");
            stkRequest.put("Amount", amount.intValue());
            stkRequest.put("PartyA", phoneNumber);
            stkRequest.put("PartyB", mpesaConfig.getShortcode());
            stkRequest.put("PhoneNumber", phoneNumber);
            stkRequest.put("CallBackURL", mpesaConfig.getCallbackUrl());
            stkRequest.put("AccountReference", accountReference);
            stkRequest.put("TransactionDesc", description);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(stkRequest, headers);

            log.info("Sending STK Push request to: {}", mpesaConfig.getStkPushUrl());
            log.info("Request body: {}", objectMapper.writeValueAsString(stkRequest));

            ResponseEntity<MpesaStkResponse> response = restTemplate.exchange(
                    mpesaConfig.getStkPushUrl(),
                    HttpMethod.POST,
                    entity,
                    MpesaStkResponse.class
            );

            MpesaStkResponse stkResponse = response.getBody();
            if (stkResponse != null) {
                stkResponse.setTimestamp(timestamp);
                log.info("STK Push response: {}", objectMapper.writeValueAsString(stkResponse));
            }

            return stkResponse;

        } catch (Exception e) {
            log.error("Error initiating STK push: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initiate M-Pesa payment", e);
        }
    }

    private String generatePassword(String timestamp) {
        try {
            String data = mpesaConfig.getShortcode() + mpesaConfig.getPasskey() + timestamp;
            return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error generating password: {}", e.getMessage());
            throw new RuntimeException("Failed to generate password", e);
        }
    }

    @Override
    public String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return null;
        }

        // Remove any non-digit characters
        String digits = phoneNumber.replaceAll("[^0-9]", "");

        // Convert to 254 format if needed
        if (digits.startsWith("0")) {
            return "254" + digits.substring(1);
        } else if (digits.startsWith("7") && digits.length() == 9) {
            return "254" + digits;
        } else if (digits.startsWith("254") && digits.length() == 12) {
            return digits;
        } else if (digits.startsWith("+254")) {
            return digits.substring(1);
        }

        // Return as-is if already in correct format
        return phoneNumber;
    }
}