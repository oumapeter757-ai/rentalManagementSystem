package com.peterscode.rentalmanagementsystem.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MaintenanceSummaryResponse {

    private Long totalRequests;
    private Long openRequests;
    private Long completedRequests;
    private Long urgentRequests;

    private Map<String, Long> requestsByCategory;
    private Map<String, Long> requestsByStatus;
    private Map<String, Long> requestsByPriority;

    private Double averageResolutionTime;
    private Long requestsLast7Days;
    private Long requestsLast30Days;
}