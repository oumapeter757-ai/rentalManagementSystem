package com.peterscode.rentalmanagementsystem.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenanceCategory;
import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenancePriority;
import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MaintenanceResponse {

    private Long id;
    private MaintenanceCategory category;
    private String categoryDisplayName;
    private String title;
    private String description;
    private MaintenancePriority priority;
    private String priorityDisplayName;
    private MaintenanceStatus status;
    private String statusDisplayName;
    private LocalDateTime requestDate;
    private LocalDateTime updatedAt;
    private String notes;

    // Tenant info
    private Long tenantId;
    private String tenantName;
    private String tenantEmail;
    private String tenantPhone;

    // Property info (ADDED)
    private Long propertyId;
    private String propertyTitle;
    private String propertyAddress;
    private String propertyType;

    // Assigned staff info
    private Long assignedToId;
    private String assignedToName;
    private String assignedToEmail;

    // Images
    private List<MaintenanceImageResponse> images;

    // Calculated fields
    private Boolean isOpen;
    private Boolean isCompleted;
    private Boolean isUrgent;
    private String daysSinceRequest;
    private String timeSinceRequest;

    // Formatted dates (computed)
    public String getFormattedRequestDate() {
        return requestDate != null ? requestDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")) : null;
    }

    public String getFormattedUpdatedAt() {
        return updatedAt != null ? updatedAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")) : null;
    }

    // Image count (computed)
    public Integer getImageCount() {
        return images != null ? images.size() : 0;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MaintenanceImageResponse {
        private Long id;
        private String imageUrl;
        private String thumbnailUrl;
        private String caption;
        private LocalDateTime uploadedAt;
        private String uploadedBy;

        // Formatted date (computed)
        public String getFormattedUploadedAt() {
            return uploadedAt != null ? uploadedAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")) : null;
        }
    }
}