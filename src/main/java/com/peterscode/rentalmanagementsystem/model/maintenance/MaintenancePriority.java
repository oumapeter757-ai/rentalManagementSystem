package com.peterscode.rentalmanagementsystem.model.maintenance;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MaintenancePriority {
    LOW("Low", "Non-urgent, can be scheduled"),
    MEDIUM("Medium", "Should be addressed soon"),
    HIGH("High", "Important, affects usability"),
    URGENT("Urgent", "Needs immediate attention"),
    EMERGENCY("Emergency", "Critical safety/security issue");

    private final String displayName;
    private final String description;
}