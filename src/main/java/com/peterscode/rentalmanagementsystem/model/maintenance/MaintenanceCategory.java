package com.peterscode.rentalmanagementsystem.model.maintenance;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MaintenanceCategory {
    PLUMBING("Plumbing", "Pipes, drains, toilets, sinks"),
    ELECTRICAL("Electrical", "Wiring, outlets, lighting, circuit breakers"),
    HVAC("HVAC", "Heating, ventilation, air conditioning"),
    APPLIANCES("Appliances", "Refrigerator, oven, dishwasher, washer/dryer"),
    STRUCTURAL("Structural", "Walls, floors, ceilings, doors, windows"),
    PEST_CONTROL("Pest Control", "Rodents, insects, termites"),
    LANDSCAPING("Landscaping", "Lawn, garden, trees, sprinklers"),
    SAFETY("Safety", "Smoke detectors, fire alarms, security"),
    GENERAL("General", "Other maintenance issues"),
    EMERGENCY("Emergency", "Immediate attention required");

    private final String displayName;
    private final String description;



}