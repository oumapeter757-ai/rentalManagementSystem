package com.peterscode.rentalmanagementsystem.service.payment;

import com.peterscode.rentalmanagementsystem.dto.response.MpesaStkResponse;
import java.math.BigDecimal;

public interface MpesaService {
    MpesaStkResponse initiateStkPush(String phoneNumber, BigDecimal amount, String accountReference, String description);
    String getAccessToken();
    String formatPhoneNumber(String phoneNumber);
}