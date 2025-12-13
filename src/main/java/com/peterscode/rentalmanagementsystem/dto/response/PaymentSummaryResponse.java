package com.peterscode.rentalmanagementsystem.dto.response;

import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class PaymentSummaryResponse {
    private BigDecimal totalAmount;
    private BigDecimal totalSuccessful;
    private BigDecimal totalPending;
    private long totalTransactions;
    private long successfulTransactions;
    private long pendingTransactions;
    private long failedTransactions;
    private long cancelledTransactions;
    private long refundedTransactions;
    private long reversedTransactions;
    private Map<String, BigDecimal> amountByMethod;
    private Map<String, Long> countByMethod;
    private Map<String, BigDecimal> dailyRevenue;
}