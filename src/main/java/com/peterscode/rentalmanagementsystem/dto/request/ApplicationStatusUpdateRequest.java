package com.peterscode.rentalmanagementsystem.dto.request;

import com.peterscode.rentalmanagementsystem.model.application.RentalApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusUpdateRequest {
    private RentalApplicationStatus status;
    private String notes;
}