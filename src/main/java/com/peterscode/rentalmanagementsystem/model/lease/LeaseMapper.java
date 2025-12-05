package com.peterscode.rentalmanagementsystem.model.lease;

import com.peterscode.rentalmanagementsystem.dto.response.LeaseResponse;

public class LeaseMapper {

    public static LeaseResponse toResponse(Lease lease) {
        return LeaseResponse.builder()
                .id(lease.getId())
                .tenantId(lease.getTenant().getId())
                .tenantEmail(lease.getTenant().getEmail())
                .propertyId(lease.getProperty().getId())
                .propertyTitle(lease.getProperty().getTitle())
                .startDate(lease.getStartDate())
                .endDate(lease.getEndDate())
                .monthlyRent(lease.getMonthlyRent())
                .deposit(lease.getDeposit())
                .status(lease.getStatus().name())
                .notes(lease.getNotes())
                .createdAt(lease.getCreatedAt())
                .updatedAt(lease.getUpdatedAt())
                .build();
    }
}
