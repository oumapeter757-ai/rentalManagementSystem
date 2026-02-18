package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.request.MpesaStkRequest;
import com.peterscode.rentalmanagementsystem.dto.request.PaymentRequest;
import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.dto.response.MpesaStkResponse;
import com.peterscode.rentalmanagementsystem.dto.response.PaymentResponse;
import com.peterscode.rentalmanagementsystem.dto.response.PaymentSummaryResponse;
import com.peterscode.rentalmanagementsystem.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;



    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT', 'ADMIN')")
    @Operation(summary = "Create a new payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            Authentication authentication,
            @Valid @RequestBody PaymentRequest request) {

        String callerEmail = authentication.getName();
        PaymentResponse response = paymentService.createPayment(request, callerEmail);
        return ResponseEntity.ok(
                ApiResponse.ok("Payment created successfully", response)
        );
    }

    @PostMapping("/mpesa/stk-push")
    @PreAuthorize("hasAnyRole('TENANT', 'ADMIN')")
    @Operation(summary = "Initiate M-Pesa STK Push payment")
    public ResponseEntity<ApiResponse<MpesaStkResponse>> initiateMpesaPayment(
            Authentication authentication,
            @Valid @RequestBody MpesaStkRequest request) {

        String callerEmail = authentication.getName();
        MpesaStkResponse response = paymentService.initiateMpesaPayment(request, callerEmail);
        return ResponseEntity.ok(
                ApiResponse.ok("M-Pesa payment initiated", response)
        );
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasAnyRole('TENANT', 'ADMIN')")
    @Operation(summary = "Initiate a new payment (Deposit/Full)")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            Authentication authentication,
            @Valid @RequestBody com.peterscode.rentalmanagementsystem.dto.request.PaymentInitiationRequest request) {

        String callerEmail = authentication.getName();
        PaymentResponse response = paymentService.initiatePayment(request, callerEmail);
        return ResponseEntity.ok(
                ApiResponse.ok("Payment initiated successfully", response)
        );
    }



    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(
            @PathVariable Long id) {

        PaymentResponse payment = paymentService.getPaymentById(id);
        return ResponseEntity.ok(
                ApiResponse.ok("Payment fetched successfully", payment)
        );
    }

    @GetMapping("/transaction/{transactionCode}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get payment by transaction code")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByTransactionCode(
            @PathVariable String transactionCode) {

        PaymentResponse payment = paymentService.getPaymentByTransactionCode(transactionCode);
        return ResponseEntity.ok(
                ApiResponse.ok("Payment fetched successfully", payment)
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Get all payments")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getAllPayments(Authentication authentication) {
        String callerEmail = authentication.getName();
        List<PaymentResponse> payments = paymentService.getAllPayments(callerEmail);
        return ResponseEntity.ok(
                ApiResponse.ok("All payments fetched successfully", payments)
        );
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('TENANT', 'LANDLORD', 'ADMIN')")
    @Operation(summary = "Get my payments")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getMyPayments(
            Authentication authentication) {

        String callerEmail = authentication.getName();
        List<PaymentResponse> payments = paymentService.getMyPayments(callerEmail);
        return ResponseEntity.ok(
                ApiResponse.ok("My payments fetched successfully", payments)
        );
    }

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAnyRole('LANDLORD', 'ADMIN')")
    @Operation(summary = "Get payments by tenant ID")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsByTenant(
            @PathVariable Long tenantId) {

        List<PaymentResponse> payments = paymentService.getPaymentsByTenant(tenantId);
        return ResponseEntity.ok(
                ApiResponse.ok("Tenant payments fetched successfully", payments)
        );
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get payments by status")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsByStatus(
            @PathVariable String status) {

        List<PaymentResponse> payments = paymentService.getPaymentsByStatus(status);
        return ResponseEntity.ok(
                ApiResponse.ok("Payments fetched by status", payments)
        );
    }

    @GetMapping("/method/{method}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get payments by payment method")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsByMethod(
            @PathVariable String method) {

        List<PaymentResponse> payments = paymentService.getPaymentsByMethod(method);
        return ResponseEntity.ok(
                ApiResponse.ok("Payments fetched by method", payments)
        );
    }

    @GetMapping("/mpesa/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get pending M-Pesa callbacks")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPendingMpesaCallbacks() {

        List<PaymentResponse> payments = paymentService.getPendingMpesaCallbacks();
        return ResponseEntity.ok(
                ApiResponse.ok("Pending M-Pesa callbacks", payments)
        );
    }

    // ==================== Payment Updates ====================

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Update payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> updatePayment(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request) {

        String callerEmail = authentication.getName();
        PaymentResponse payment = paymentService.updatePayment(id, request, callerEmail);
        return ResponseEntity.ok(
                ApiResponse.ok("Payment updated successfully", payment)
        );
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Update payment status")
    public ResponseEntity<ApiResponse<PaymentResponse>> updatePaymentStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String notes) {

        PaymentResponse payment = paymentService.updatePaymentStatus(id, status, notes);
        return ResponseEntity.ok(
                ApiResponse.ok("Payment status updated", payment)
        );
    }

    @PutMapping("/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Manually mark payment as paid")
    public ResponseEntity<ApiResponse<PaymentResponse>> markAsPaid(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) String transactionCode) {

        String callerEmail = authentication.getName();
        PaymentResponse payment = paymentService.markAsPaid(id, transactionCode, callerEmail);
        return ResponseEntity.ok(
                ApiResponse.ok("Payment marked as paid", payment)
        );
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reverse a successful payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> reversePayment(
            @PathVariable Long id,
            @RequestParam String reversalReason,
            Authentication authentication) {

        String callerEmail = authentication.getName();
        PaymentResponse response = paymentService.reversePayment(id, reversalReason, callerEmail);
        return ResponseEntity.ok(
                ApiResponse.ok("Payment reversed successfully", response)
        );
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Refund a payment (full or partial)")
    public ResponseEntity<ApiResponse<PaymentResponse>> refundPayment(
            @PathVariable Long id,
            @RequestParam BigDecimal refundAmount,
            @RequestParam String reason,
            Authentication authentication) {

        String callerEmail = authentication.getName();
        PaymentResponse response = paymentService.refundPayment(id, refundAmount, reason, callerEmail);
        return ResponseEntity.ok(
                ApiResponse.ok("Refund processed successfully", response)
        );
    }

    @PostMapping("/mpesa/query-status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Manually query M-Pesa transaction status")
    public ResponseEntity<ApiResponse<String>> queryMpesaTransactionStatus(
            @RequestParam String checkoutRequestId) {

        paymentService.queryMpesaTransactionStatus(checkoutRequestId);
        return ResponseEntity.ok(
                ApiResponse.ok("Transaction status query initiated", "Query sent for: " + checkoutRequestId)
        );
    }

    // ==================== Analytics & Reports ====================

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Get payment summary")
    public ResponseEntity<ApiResponse<PaymentSummaryResponse>> getPaymentSummary() {

        PaymentSummaryResponse summary = paymentService.getPaymentSummary();
        return ResponseEntity.ok(
                ApiResponse.ok("Payment summary fetched", summary)
        );
    }

    @GetMapping("/revenue/total")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get total revenue")
    public ResponseEntity<ApiResponse<BigDecimal>> getTotalRevenue() {

        BigDecimal totalRevenue = paymentService.getTotalRevenue();
        return ResponseEntity.ok(
                ApiResponse.ok("Total revenue calculated", totalRevenue)
        );
    }

    @GetMapping("/revenue/tenant/{tenantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Get total revenue by tenant")
    public ResponseEntity<ApiResponse<BigDecimal>> getTotalRevenueByTenant(
            @PathVariable Long tenantId) {

        BigDecimal revenue = paymentService.getTotalRevenueByTenant(tenantId);
        return ResponseEntity.ok(
                ApiResponse.ok("Total revenue by tenant calculated", revenue)
        );
    }

    // ==================== Payment Status Checks ====================

    @GetMapping("/{id}/success")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check if payment is successful")
    public ResponseEntity<ApiResponse<Boolean>> isPaymentSuccessful(@PathVariable Long id) {

        boolean isSuccessful = paymentService.isPaymentSuccessful(id);
        return ResponseEntity.ok(
                ApiResponse.ok("Payment success status checked", isSuccessful)
        );
    }

    @GetMapping("/{id}/pending")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check if payment is pending")
    public ResponseEntity<ApiResponse<Boolean>> isPaymentPending(@PathVariable Long id) {

        boolean isPending = paymentService.isPaymentPending(id);
        return ResponseEntity.ok(
                ApiResponse.ok("Payment pending status checked", isPending)
        );
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get payment status")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentStatus(@PathVariable Long id) {
        // We can reuse PaymentResponse or create a smaller DTO. 
        // For now, let's return the full PaymentResponse as it contains the status.
        PaymentResponse payment = paymentService.getPaymentById(id);
        // But the frontend expects a simple status object or the full object.
        // paymentService.js expect response.data.
        return ResponseEntity.ok(
                ApiResponse.ok("Payment status fetched", payment) 
        );
    }

    // ==================== Utility Endpoints ====================

    @GetMapping("/generate-transaction-code")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Generate a new transaction code")
    public ResponseEntity<ApiResponse<String>> generateTransactionCode() {

        String transactionCode = paymentService.generateTransactionCode();
        return ResponseEntity.ok(
                ApiResponse.ok("Transaction code generated", transactionCode)
        );
    }

    // ==================== Deletion ====================

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete payment")
    public ResponseEntity<ApiResponse<Void>> deletePayment(
            Authentication authentication,
            @PathVariable Long id) {

        String callerEmail = authentication.getName();
        paymentService.deletePayment(id, callerEmail);
        return ResponseEntity.ok(
                ApiResponse.ok("Payment deleted successfully", null)
        );
    }
}

@RestController
@RequestMapping("/api/payments/mpesa")
@RequiredArgsConstructor
@Slf4j
class MpesaCallbackController {

    private final PaymentService paymentService;

    @PostMapping("/callback")
    @Operation(summary = "M-Pesa STK Push callback endpoint")
    public ResponseEntity<String> handleStkCallback(@RequestBody String callbackData) {
        log.info("Received M-Pesa STK callback");

        try {
            paymentService.processMpesaCallback(callbackData);
            return ResponseEntity.ok("{\"ResultCode\":0,\"ResultDesc\":\"Callback processed successfully\"}");
        } catch (Exception e) {
            log.error("Error processing STK callback: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body("{\"ResultCode\":1,\"ResultDesc\":\"Error processing callback\"}");
        }
    }

    @PostMapping("/validation")
    @Operation(summary = "M-Pesa validation endpoint (for C2B)")
    public ResponseEntity<String> handleValidation(@RequestBody String validationData) {
        log.info("Received M-Pesa validation request: {}", validationData);
        return ResponseEntity.ok("Accepted");
    }

    @PostMapping("/confirmation")
    @Operation(summary = "M-Pesa confirmation endpoint (for C2B)")
    public ResponseEntity<String> handleConfirmation(@RequestBody String confirmationData) {
        log.info("Received M-Pesa confirmation request: {}", confirmationData);
        try {
            paymentService.processMpesaCallback(confirmationData);
            return ResponseEntity.ok("Confirmation received and processed");
        } catch (Exception e) {
            log.error("Error processing M-Pesa confirmation", e);
            return ResponseEntity.status(500).body("Error processing confirmation");
        }
    }
}