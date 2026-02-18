package com.peterscode.rentalmanagementsystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiationResponse {
    private Long paymentId;
    private BigDecimal amount;
    private String paymentType;
    private String paymentMethod;
    private String status;
    private String message;
    
    // M-Pesa specific fields
    private String checkoutRequestID;
    private String merchantRequestID;
}
