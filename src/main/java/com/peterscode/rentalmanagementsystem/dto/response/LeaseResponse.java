package com.peterscode.rentalmanagementsystem.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class LeaseResponse {
    private Long id;
    private Long tenantId;
    private String tenantEmail;
    private String tenantFirstName;
    private String tenantLastName;
    private String tenantPhoneNumber;
    private Long propertyId;
    private String propertyTitle;
    private String propertyAddress;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal monthlyRent;
    private BigDecimal deposit;
    private Boolean depositPaid;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
