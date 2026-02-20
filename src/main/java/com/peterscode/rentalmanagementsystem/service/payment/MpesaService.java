package com.peterscode.rentalmanagementsystem.service.payment;

import com.peterscode.rentalmanagementsystem.dto.request.MpesaStkRequest;
import com.peterscode.rentalmanagementsystem.dto.response.MpesaStkResponse;
import com.peterscode.rentalmanagementsystem.dto.response.MpesaTransactionStatusResponse;

import java.math.BigDecimal;

public interface MpesaService {
    String getAccessToken();
    MpesaStkResponse initiateStkPush(String phoneNumber, BigDecimal amount,
                                     String accountReference, String description);
    MpesaTransactionStatusResponse queryTransactionStatus(String checkoutRequestId);
    String formatPhoneNumber(String phoneNumber);
    String generateTimestamp();
    String generatePassword(String timestamp);

    MpesaStkResponse initiateStkPush(MpesaStkRequest stkRequest);
}