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
public class MpesaStkPushRequest {
    private String phoneNumber;
    private BigDecimal amount;
    private String accountReference;
    private String transactionDesc;
}
