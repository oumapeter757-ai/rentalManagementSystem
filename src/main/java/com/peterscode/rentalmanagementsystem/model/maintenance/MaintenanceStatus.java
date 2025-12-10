package com.peterscode.rentalmanagementsystem.model.maintenance;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MaintenanceStatus {
    PENDING("Pending", "Awaiting review"),
    REVIEWED("Reviewed", "Under review by landlord"),
    APPROVED("Approved", "Approved for work"),
    IN_PROGRESS("In Progress", "Work has started"),
    ON_HOLD("On Hold", "Work paused"),
    COMPLETED("Completed", "Work finished"),
    CANCELLED("Cancelled", "Request cancelled"),
    REJECTED("Rejected", "Request denied");

    private final String displayName;
    private final String description;

    public boolean isOpen() {
        return this == PENDING || this == REVIEWED || this == APPROVED ||
                this == IN_PROGRESS || this == ON_HOLD;
    }

    public boolean isCompleted() {
        return this == COMPLETED || this == CANCELLED || this == REJECTED;
    }
}