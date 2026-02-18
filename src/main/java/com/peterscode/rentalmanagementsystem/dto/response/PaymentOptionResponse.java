package com.peterscode.rentalmanagementsystem.dto.response;

import com.peterscode.rentalmanagementsystem.model.payment.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOptionResponse {
    
    private PaymentType paymentType;
    
    private BigDecimal amount;
    
    private String description;
    
    private Boolean available;
}
