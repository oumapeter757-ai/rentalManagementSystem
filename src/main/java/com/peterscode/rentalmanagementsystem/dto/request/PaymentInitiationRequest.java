package com.peterscode.rentalmanagementsystem.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiationRequest {
    private Long propertyId;
    private String paymentType; // "DEPOSIT" or "FULL_AMOUNT"
    private String paymentMethod; // "MPESA" or "CARD"
    private String phoneNumber;
    private Long leaseId;// For M-Pesa (format: 254712345678)
    private String notes;
    private String paymentToken; // Stripe Token or PaymentMethod ID
}
