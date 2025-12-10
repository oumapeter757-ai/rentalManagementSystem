
package com.peterscode.rentalmanagementsystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailStatisticsResponse {
    private long totalEmails;
    private long sentCount;
    private long failedCount;
    private long pendingCount;
    private long deliveredCount;
    private long openedCount;
    private double successRate;
    private long last24Hours;
    private long last7Days;
}