package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.request.PaymentInitiationRequest;
import com.peterscode.rentalmanagementsystem.dto.response.*;
import com.peterscode.rentalmanagementsystem.service.lease.LeaseService;
import com.peterscode.rentalmanagementsystem.service.payment.MonthlyPaymentHistoryService;
import com.peterscode.rentalmanagementsystem.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant", description = "Tenant-specific endpoints")
public class TenantController {

    private final LeaseService leaseService;
    private final PaymentService paymentService;
    private final MonthlyPaymentHistoryService monthlyPaymentHistoryService;

    @GetMapping("/me/leases")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Get my leases")
    public ResponseEntity<ApiResponse<List<LeaseResponse>>> getMyLeases(Authentication authentication) {
        String email = authentication.getName();
        List<LeaseResponse> leases = leaseService.getMyLeases(email);
        return ResponseEntity.ok(ApiResponse.success("Leases retrieved successfully", leases));
    }

    @GetMapping("/me/payments")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Get my payments")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getMyPayments(Authentication authentication) {
        String email = authentication.getName();
        List<PaymentResponse> payments = paymentService.getMyPayments(email);
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully", payments));
    }

    @GetMapping("/leases/{leaseId}/payment-options")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Get payment options for a lease")
    public ResponseEntity<ApiResponse<List<PaymentOptionResponse>>> getPaymentOptions(
            @PathVariable Long leaseId,
            Authentication authentication) {
        String email = authentication.getName();
        List<PaymentOptionResponse> options = paymentService.getPaymentOptions(leaseId, email);
        return ResponseEntity.ok(ApiResponse.success("Payment options retrieved successfully", options));
    }

    @PostMapping("/payments/initiate")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Initiate a payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @RequestBody PaymentInitiationRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        PaymentResponse payment = paymentService.initiatePayment(request, email);
        return ResponseEntity.ok(ApiResponse.success("Payment initiated successfully. Check your phone for M-Pesa prompt.", payment));
    }

    // ── Monthly Payment History Endpoints ──────────────────────────────

    @GetMapping("/me/payment-history/current")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Get current month payment history")
    public ResponseEntity<ApiResponse<MonthlyPaymentHistoryResponse>> getCurrentMonthHistory(
            Authentication authentication) {
        String email = authentication.getName();
        MonthlyPaymentHistoryResponse history = monthlyPaymentHistoryService.getCurrentMonthHistory(email);
        return ResponseEntity.ok(ApiResponse.success("Current month history retrieved successfully", history));
    }

    @GetMapping("/me/payment-history")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Get all payment history")
    public ResponseEntity<ApiResponse<List<MonthlyPaymentHistoryResponse>>> getAllPaymentHistory(
            Authentication authentication) {
        String email = authentication.getName();
        List<MonthlyPaymentHistoryResponse> history = monthlyPaymentHistoryService.getTenantPaymentHistory(email);
        return ResponseEntity.ok(ApiResponse.success("Payment history retrieved successfully", history));
    }

    @GetMapping("/me/payment-history/{year}")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Get payment history by year")
    public ResponseEntity<ApiResponse<List<MonthlyPaymentHistoryResponse>>> getPaymentHistoryByYear(
            @PathVariable Integer year,
            Authentication authentication) {
        String email = authentication.getName();
        List<MonthlyPaymentHistoryResponse> history = monthlyPaymentHistoryService.getTenantPaymentHistoryByYear(email, year);
        return ResponseEntity.ok(ApiResponse.success("Payment history retrieved successfully", history));
    }

    @GetMapping("/me/payment-summary")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Get payment summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentSummary(
            Authentication authentication) {
        String email = authentication.getName();
        BigDecimal totalPaid = monthlyPaymentHistoryService.getTenantTotalPaid(email);
        BigDecimal balance = monthlyPaymentHistoryService.getTenantTotalBalance(email);

        Map<String, Object> summary = Map.of(
                "totalPaid", totalPaid,
                "balance", balance,
                "hasOutstandingBalance", balance.compareTo(BigDecimal.ZERO) > 0
        );
        return ResponseEntity.ok(ApiResponse.success("Payment summary retrieved successfully", summary));
    }
}
