package com.peterscode.rentalmanagementsystem.service.payment;

import com.peterscode.rentalmanagementsystem.dto.request.MpesaStkRequest;
import com.peterscode.rentalmanagementsystem.dto.request.PaymentRequest;
import com.peterscode.rentalmanagementsystem.dto.response.MpesaStkResponse;
import com.peterscode.rentalmanagementsystem.dto.response.PaymentResponse;
import com.peterscode.rentalmanagementsystem.dto.response.PaymentSummaryResponse;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentService {

    // Create payment
    PaymentResponse createPayment(PaymentRequest request, String callerEmail);
    MpesaStkResponse initiateMpesaPayment(MpesaStkRequest request, String callerEmail);

    // Read operations
    PaymentResponse getPaymentById(Long id);
    PaymentResponse getPaymentByTransactionCode(String transactionCode);
    List<PaymentResponse> getAllPayments();
    List<PaymentResponse> getPaymentsByTenant(Long tenantId);
    List<PaymentResponse> getPaymentsByStatus(String status);
    List<PaymentResponse> getPaymentsByMethod(String method);
    List<PaymentResponse> getMyPayments(String callerEmail);
    List<PaymentResponse> getPendingMpesaCallbacks();

    // Update operations
    PaymentResponse updatePaymentStatus(Long id, String status, String notes);
    PaymentResponse updatePayment(Long id, PaymentRequest request, String callerEmail);
    void processMpesaCallback(String callbackData);
    PaymentResponse markAsPaid(Long id, String transactionCode, String callerEmail);

    // Summary and statistics
    PaymentSummaryResponse getPaymentSummary();
    //PaymentSummaryResponse getPaymentSummaryByTenant(Long tenantId);
    BigDecimal getTotalRevenue();
    BigDecimal getTotalRevenueByTenant(Long tenantId);

    // Utility methods
    boolean isPaymentSuccessful(Long id);
    boolean isPaymentPending(Long id);
    String generateTransactionCode();

    // Delete operations
    void deletePayment(Long id, String callerEmail);
}