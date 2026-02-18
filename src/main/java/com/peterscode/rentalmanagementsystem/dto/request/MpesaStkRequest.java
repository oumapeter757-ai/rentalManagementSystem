package com.peterscode.rentalmanagementsystem.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MpesaStkRequest {

    @NotNull(message = "Tenant ID is required")
    private Long tenantId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
    @DecimalMax(value = "70000.00", message = "Amount cannot exceed 70,000 for M-Pesa")
    private BigDecimal amount;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^254[17][0-9]{8}$", message = "Phone number must be in format 2547XXXXXXXX")
    private String phoneNumber;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    private String transactionCode;

    private String accountReference;
}