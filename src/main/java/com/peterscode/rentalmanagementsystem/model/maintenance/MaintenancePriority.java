package com.peterscode.rentalmanagementsystem.model.maintenance;

public enum MaintenancePriority {
    LOW("Low", "Non-urgent, can be scheduled"),
    MEDIUM("Medium", "Should be addressed soon"),
    HIGH("High", "Important, affects usability"),
    URGENT("Urgent", "Needs immediate attention"),
    EMERGENCY("Emergency", "Critical safety/security issue");

    private final String displayName;
    private final String description;

    MaintenancePriority(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}