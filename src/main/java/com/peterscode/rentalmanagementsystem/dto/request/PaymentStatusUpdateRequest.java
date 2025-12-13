package com.peterscode.rentalmanagementsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PaymentStatusUpdateRequest {

    @NotBlank(message = "Status is required")
    private String status;

    private String notes;

    private String transactionCode; // For manual updates
}