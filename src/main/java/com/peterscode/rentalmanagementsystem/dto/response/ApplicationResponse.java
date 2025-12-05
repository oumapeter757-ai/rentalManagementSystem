package com.peterscode.rentalmanagementsystem.dto.response;

import com.peterscode.rentalmanagementsystem.model.application.RentalApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationResponse {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String tenantEmail;
    private Long propertyId;
    private String propertyTitle;
    private String propertyAddress;
    private Double propertyRent;
    private RentalApplicationStatus status;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}