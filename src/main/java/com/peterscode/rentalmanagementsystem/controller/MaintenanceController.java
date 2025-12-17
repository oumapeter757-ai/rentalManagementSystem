package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.request.MaintenanceRequestDto;
import com.peterscode.rentalmanagementsystem.dto.request.MaintenanceStatusUpdateDto;
import com.peterscode.rentalmanagementsystem.dto.request.MaintenanceUpdateDto;
import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.dto.response.MaintenanceResponse;
import com.peterscode.rentalmanagementsystem.dto.response.MaintenanceSummaryResponse;

import com.peterscode.rentalmanagementsystem.service.maintenance.MaintenanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Maintenance", description = "Maintenance request management endpoints")
@Slf4j
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Create a new maintenance request")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> createMaintenanceRequest(
            Authentication authentication,
            @Valid @ModelAttribute MaintenanceRequestDto requestDto,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {

        String tenantEmail = authentication.getName();

        // Set images from multipart if provided
        if (images != null && !images.isEmpty()) {
            requestDto.setImages(images);
        }

        MaintenanceResponse response = maintenanceService.createMaintenanceRequest(requestDto, tenantEmail);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Maintenance request created successfully", response));
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add images to existing maintenance request")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> addImagesToRequest(
            Authentication authentication,
            @PathVariable Long id,
            @RequestPart("images") List<MultipartFile> images) {

        String callerEmail = authentication.getName();
        MaintenanceResponse response = maintenanceService.addImagesToRequest(id, images, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Images added successfully", response)
        );
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all maintenance requests (role-based)")
    public ResponseEntity<ApiResponse<List<MaintenanceResponse>>> getAllMaintenanceRequests(
            Authentication authentication) {

        String callerEmail = authentication.getName();
        List<MaintenanceResponse> responses = maintenanceService.getAllMaintenanceRequests(callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Maintenance requests fetched successfully", responses)
        );
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Get my maintenance requests")
    public ResponseEntity<ApiResponse<List<MaintenanceResponse>>> getMyMaintenanceRequests(
            Authentication authentication) {

        String tenantEmail = authentication.getName();
        List<MaintenanceResponse> responses = maintenanceService.getMyMaintenanceRequests(tenantEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("My maintenance requests fetched successfully", responses)
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get maintenance request by ID")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> getMaintenanceRequestById(
            Authentication authentication,
            @PathVariable Long id) {

        String callerEmail = authentication.getName();
        MaintenanceResponse response = maintenanceService.getMaintenanceRequestById(id, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Maintenance request fetched successfully", response)
        );
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get maintenance requests by status")
    public ResponseEntity<ApiResponse<List<MaintenanceResponse>>> getRequestsByStatus(
            Authentication authentication,
            @PathVariable String status) {

        String callerEmail = authentication.getName();
        List<MaintenanceResponse> responses = maintenanceService.getRequestsByStatus(status, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Maintenance requests by status fetched successfully", responses)
        );
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get maintenance requests by category")
    public ResponseEntity<ApiResponse<List<MaintenanceResponse>>> getRequestsByCategory(
            Authentication authentication,
            @PathVariable String category) {

        String callerEmail = authentication.getName();
        List<MaintenanceResponse> responses = maintenanceService.getRequestsByCategory(category, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Maintenance requests by category fetched successfully", responses)
        );
    }

    @GetMapping("/priority/{priority}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get maintenance requests by priority")
    public ResponseEntity<ApiResponse<List<MaintenanceResponse>>> getRequestsByPriority(
            Authentication authentication,
            @PathVariable String priority) {

        String callerEmail = authentication.getName();
        List<MaintenanceResponse> responses = maintenanceService.getRequestsByPriority(priority, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Maintenance requests by priority fetched successfully", responses)
        );
    }

    @GetMapping("/open")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get open maintenance requests")
    public ResponseEntity<ApiResponse<List<MaintenanceResponse>>> getOpenRequests(
            Authentication authentication) {

        String callerEmail = authentication.getName();
        List<MaintenanceResponse> responses = maintenanceService.getOpenRequests(callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Open maintenance requests fetched successfully", responses)
        );
    }

    @GetMapping("/open/count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get count of open maintenance requests")
    public ResponseEntity<ApiResponse<Long>> getOpenRequestsCount(
            Authentication authentication) {

        String callerEmail = authentication.getName();
        Long count = maintenanceService.getOpenRequestsCount(callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Open maintenance requests count fetched", count)
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update maintenance request")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> updateMaintenanceRequest(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody MaintenanceUpdateDto updateDto) {

        String callerEmail = authentication.getName();
        MaintenanceResponse response = maintenanceService.updateMaintenanceRequest(id, updateDto, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Maintenance request updated successfully", response)
        );
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Update maintenance request status")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> updateMaintenanceStatus(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody MaintenanceStatusUpdateDto statusUpdateDto) {

        String callerEmail = authentication.getName();
        MaintenanceResponse response = maintenanceService.updateStatus(
                id,
                statusUpdateDto.getStatus().name(),
                statusUpdateDto.getResolutionNotes(),
                callerEmail
        );

        return ResponseEntity.ok(
                ApiResponse.ok("Maintenance request status updated successfully", response)
        );
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Assign maintenance request to staff")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> assignMaintenanceRequest(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam Long staffId) {

        String callerEmail = authentication.getName();
        MaintenanceResponse response = maintenanceService.assignRequest(id, staffId, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Maintenance request assigned successfully", response)
        );
    }

    @PutMapping("/{id}/notes")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add notes to maintenance request")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> addNoteToRequest(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam String note) {

        String callerEmail = authentication.getName();
        MaintenanceResponse response = maintenanceService.addNote(id, note, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Note added successfully", response)
        );
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Get maintenance summary")
    public ResponseEntity<ApiResponse<MaintenanceSummaryResponse>> getMaintenanceSummary(
            Authentication authentication) {

        String callerEmail = authentication.getName();
        MaintenanceSummaryResponse summary = maintenanceService.getMaintenanceSummary(callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Maintenance summary fetched successfully", summary)
        );
    }

    @GetMapping("/summary/me")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Get my maintenance summary")
    public ResponseEntity<ApiResponse<MaintenanceSummaryResponse>> getMyMaintenanceSummary(
            Authentication authentication) {

        String tenantEmail = authentication.getName();
        MaintenanceSummaryResponse summary = maintenanceService.getTenantMaintenanceSummary(tenantEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("My maintenance summary fetched successfully", summary)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete maintenance request")
    public ResponseEntity<ApiResponse<Void>> deleteMaintenanceRequest(
            Authentication authentication,
            @PathVariable Long id) {

        String callerEmail = authentication.getName();
        maintenanceService.deleteMaintenanceRequest(id, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Maintenance request deleted successfully", null)
        );
    }

    @DeleteMapping("/{requestId}/images/{imageId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete image from maintenance request")
    public ResponseEntity<ApiResponse<Void>> deleteMaintenanceImage(
            Authentication authentication,
            @PathVariable Long requestId,
            @PathVariable Long imageId) {

        String callerEmail = authentication.getName();
        maintenanceService.deleteImage(requestId, imageId, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Image deleted successfully", null)
        );
    }

    @GetMapping("/{id}/accessible")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check if maintenance request is accessible")
    public ResponseEntity<ApiResponse<Boolean>> isRequestAccessible(
            Authentication authentication,
            @PathVariable Long id) {

        String callerEmail = authentication.getName();
        boolean isAccessible = maintenanceService.isRequestAccessible(id, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Accessibility check completed", isAccessible)
        );
    }

    @GetMapping("/{id}/can-update")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check if maintenance request can be updated")
    public ResponseEntity<ApiResponse<Boolean>> canUpdateRequest(
            Authentication authentication,
            @PathVariable Long id) {

        String callerEmail = authentication.getName();
        boolean canUpdate = maintenanceService.canUpdateRequest(id, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Update permission check completed", canUpdate)
        );
    }
}