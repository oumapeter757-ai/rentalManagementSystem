package com.peterscode.rentalmanagementsystem.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {

    @NotNull(message = "Tenant ID is required")
    private Long tenantId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than 0")
    @DecimalMax(value = "1000000.00", message = "Amount cannot exceed 1,000,000")
    private BigDecimal amount;

    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "^(MPESA|CASH|BANK_TRANSFER|CHEQUE|CREDIT_CARD|DEBIT_CARD|MOBILE_WALLET|OTHER)$",
            message = "Invalid payment method")
    private String paymentMethod;

    @Pattern(regexp = "^254[17][0-9]{8}$", message = "Phone number must be in format 2547XXXXXXXX")
    private String phoneNumber;

    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;

    private String paymentToken; // Stripe Token or PaymentMethod ID
}