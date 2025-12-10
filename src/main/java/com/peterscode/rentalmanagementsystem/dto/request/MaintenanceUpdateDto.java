package com.peterscode.rentalmanagementsystem.dto.request;

import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenanceStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class MaintenanceUpdateDto {

    @Size(max = 2000, message = "Update notes cannot exceed 2000 characters")
    private String notes;

    private MaintenanceStatus status;

    private Long assignedToId;

    private List<MultipartFile> newImages;
}