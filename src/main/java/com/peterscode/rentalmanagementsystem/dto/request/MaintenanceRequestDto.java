package com.peterscode.rentalmanagementsystem.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenanceCategory;
import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenancePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class MaintenanceRequestDto {
    private Long propertyId;
    @NotNull(message = "Category is required")
    private MaintenanceCategory category;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @NotNull(message = "Priority is required")
    private MaintenancePriority priority;

    @JsonIgnore
    private List<MultipartFile> images;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
}