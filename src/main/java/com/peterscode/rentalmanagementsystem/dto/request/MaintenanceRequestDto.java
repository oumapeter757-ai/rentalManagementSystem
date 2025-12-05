package com.peterscode.rentalmanagementsystem.dto.request;


import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenanceCategory;
import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenancePriority;
import lombok.Data;

@Data
public class MaintenanceRequestCreateDTO {

    private Long tenantId;

    private MaintenanceCategory category;

    private String title;

    private String description;

    private MaintenancePriority priority;
}
