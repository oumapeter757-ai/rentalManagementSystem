package com.peterscode.rentalmanagementsystem.model.lease;

import com.peterscode.rentalmanagementsystem.dto.response.LeaseResponse;
import com.peterscode.rentalmanagementsystem.model.user.User;

public class LeaseMapper {

    public static LeaseResponse toResponse(Lease lease) {
        User tenant = lease.getTenant();
        return LeaseResponse.builder()
                .id(lease.getId())
                .tenantId(tenant.getId())
                .tenantEmail(tenant.getEmail())
                .tenantFirstName(resolveName(tenant.getFirstName(), tenant.getUsername(), tenant.getEmail()))
                .tenantLastName(tenant.getLastName() != null ? tenant.getLastName() : "")
                .tenantPhoneNumber(tenant.getPhoneNumber())
                .propertyId(lease.getProperty().getId())
                .propertyTitle(lease.getProperty().getTitle())
                .propertyAddress(lease.getProperty().getAddress())
                .startDate(lease.getStartDate())
                .endDate(lease.getEndDate())
                .monthlyRent(lease.getMonthlyRent())
                .deposit(lease.getDeposit())
                .depositPaid(lease.getDepositPaid())
                .status(lease.getStatus().name())
                .notes(lease.getNotes())
                .createdAt(lease.getCreatedAt())
                .updatedAt(lease.getUpdatedAt())
                .build();
    }

    private static String resolveName(String firstName, String username, String email) {
        if (firstName != null && !firstName.isBlank()) return firstName;
        if (username != null && !username.isBlank()) return username;
        if (email != null) return email.split("@")[0];
        return "User";
    }
}
