package com.peterscode.rentalmanagementsystem.service.maintenance;

import com.peterscode.rentalmanagementsystem.dto.request.MaintenanceRequestDto;
import com.peterscode.rentalmanagementsystem.dto.request.MaintenanceUpdateDto;
import com.peterscode.rentalmanagementsystem.dto.response.MaintenanceResponse;
import com.peterscode.rentalmanagementsystem.dto.response.MaintenanceSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MaintenanceService {

    // Create
    MaintenanceResponse createMaintenanceRequest(MaintenanceRequestDto requestDto, String tenantEmail);
    MaintenanceResponse addImagesToRequest(Long requestId, List<MultipartFile> images, String callerEmail);

    // Read
    MaintenanceResponse getMaintenanceRequestById(Long id, String callerEmail);
    List<MaintenanceResponse> getAllMaintenanceRequests(String callerEmail);
    List<MaintenanceResponse> getMyMaintenanceRequests(String tenantEmail);
    List<MaintenanceResponse> getRequestsByStatus(String status, String callerEmail);
    List<MaintenanceResponse> getRequestsByCategory(String category, String callerEmail);
    List<MaintenanceResponse> getRequestsByPriority(String priority, String callerEmail);
    List<MaintenanceResponse> getOpenRequests(String callerEmail);

    // Update
    MaintenanceResponse updateMaintenanceRequest(Long id, MaintenanceUpdateDto updateDto, String callerEmail);
    MaintenanceResponse updateStatus(Long id, String status, String notes, String callerEmail);
    MaintenanceResponse assignRequest(Long requestId, Long staffId, String callerEmail);
    MaintenanceResponse addNote(Long requestId, String note, String callerEmail);

    // Delete
    void deleteMaintenanceRequest(Long id, String callerEmail);
    void deleteImage(Long requestId, Long imageId, String callerEmail);

    // Summary & Statistics
    MaintenanceSummaryResponse getMaintenanceSummary(String callerEmail);
    MaintenanceSummaryResponse getTenantMaintenanceSummary(String tenantEmail);
    Long getOpenRequestsCount(String callerEmail);

    // Utility
    boolean isRequestAccessible(Long requestId, String userEmail);
    boolean canUpdateRequest(Long requestId, String userEmail);
}