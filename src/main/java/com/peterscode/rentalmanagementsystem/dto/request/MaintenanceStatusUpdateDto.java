package com.peterscode.rentalmanagementsystem.dto.request;

import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MaintenanceStatusUpdateDto {

    @NotNull(message = "Status is required")
    private MaintenanceStatus status;

    @Size(max = 2000, message = "Resolution notes cannot exceed 2000 characters")
    private String resolutionNotes;
}