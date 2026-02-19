package com.peterscode.rentalmanagementsystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyPaymentHistoryResponse {
    private Long id;
    private Long tenantId;
    private String tenantEmail;
    private Long propertyId;
    private String propertyTitle;
    private Integer month;
    private Integer year;
    private BigDecimal totalDue;
    private BigDecimal totalPaid;
    private BigDecimal balance;
    private String status;
    private LocalDate paymentDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

