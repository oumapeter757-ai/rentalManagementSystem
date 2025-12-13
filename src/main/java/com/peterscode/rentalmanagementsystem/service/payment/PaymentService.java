package com.peterscode.rentalmanagementsystem.service.payment;

import com.peterscode.rentalmanagementsystem.dto.request.MpesaStkRequest;
import com.peterscode.rentalmanagementsystem.dto.request.PaymentRequest;
import com.peterscode.rentalmanagementsystem.dto.response.MpesaStkResponse;
import com.peterscode.rentalmanagementsystem.dto.response.PaymentResponse;
import com.peterscode.rentalmanagementsystem.dto.response.PaymentSummaryResponse;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentService {
    PaymentResponse createPayment(PaymentRequest request, String callerEmail);
    MpesaStkResponse initiateMpesaPayment(MpesaStkRequest request, String callerEmail);
    void processMpesaCallback(String callbackData);
    PaymentResponse getPaymentById(Long id);
    PaymentResponse getPaymentByTransactionCode(String transactionCode);
    List<PaymentResponse> getAllPayments();
    List<PaymentResponse> getPaymentsByTenant(Long tenantId);
    List<PaymentResponse> getPaymentsByStatus(String status);
    List<PaymentResponse> getPaymentsByMethod(String method);
    List<PaymentResponse> getMyPayments(String callerEmail);
    List<PaymentResponse> getPendingMpesaCallbacks();
    PaymentResponse updatePaymentStatus(Long id, String status, String notes);
    PaymentResponse updatePayment(Long id, PaymentRequest request, String callerEmail);
    PaymentResponse markAsPaid(Long id, String transactionCode, String callerEmail);
    PaymentSummaryResponse getPaymentSummary();
    BigDecimal getTotalRevenue();
    BigDecimal getTotalRevenueByTenant(Long tenantId);
    boolean isPaymentSuccessful(Long id);
    boolean isPaymentPending(Long id);
    String generateTransactionCode();
    void deletePayment(Long id, String callerEmail);

    // New methods for enhanced flow
    PaymentResponse reversePayment(Long id, String reversalReason, String callerEmail);
    PaymentResponse refundPayment(Long id, BigDecimal refundAmount, String reason, String callerEmail);
    void queryMpesaTransactionStatus(String checkoutRequestId);
}