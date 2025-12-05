package com.peterscode.rentalmanagementsystem.dto.response;

import com.peterscode.rentalmanagementsystem.model.payment.Payment;
import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String tenantEmail;
    private String phoneNumber;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private String transactionCode;
    private boolean callbackReceived;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String notes;
    private String gatewayResponseSummary;

    public static PaymentResponse fromEntity(Payment payment) {
        String gatewaySummary = null;
        if (payment.getGatewayResponse() != null && payment.getGatewayResponse().length() > 100) {
            gatewaySummary = payment.getGatewayResponse().substring(0, 100) + "...";
        } else {
            gatewaySummary = payment.getGatewayResponse();
        }

        return PaymentResponse.builder()
                .id(payment.getId())
                .tenantId(payment.getTenant().getId())
                .tenantName(payment.getTenant().getFirstName() + " " + payment.getTenant().getLastName())
                .tenantEmail(payment.getTenant().getEmail())
                .phoneNumber(payment.getPhoneNumber())
                .amount(payment.getAmount())
                .paymentMethod(payment.getMethod().name())
                .status(payment.getStatus().name())
                .transactionCode(payment.getTransactionCode())
                .callbackReceived(payment.isCallbackReceived())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .notes(payment.getNotes())
                .gatewayResponseSummary(gatewaySummary)
                .build();
    }
}