package com.peterscode.rentalmanagementsystem.controller;


import com.peterscode.rentalmanagementsystem.dto.request.ApplicationRequest;
import com.peterscode.rentalmanagementsystem.dto.request.ApplicationStatusUpdateRequest;
import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.dto.response.ApplicationResponse;
import com.peterscode.rentalmanagementsystem.model.application.RentalApplicationStatus;

import com.peterscode.rentalmanagementsystem.service.rentalApplication.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Tag(name = "Rental Application", description = "Rental application management APIs")
public class ApplicationController {

    private final ApplicationService applicationService;

    @Operation(summary = "Create a rental application", description = "Tenants can apply for properties")
    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<ApplicationResponse>> createApplication(
            @RequestBody ApplicationRequest request,
            HttpServletRequest httpRequest) {
        ApplicationResponse application = applicationService.createApplication(request);
        ApiResponse<ApplicationResponse> response = ApiResponse.success(
                "Application submitted successfully",
                application
        );
        response.setPath(httpRequest.getRequestURI());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get application by ID", description = "Get details of a specific application")
    @GetMapping("/{applicationId}")
    @PreAuthorize("hasAnyRole('TENANT', 'LANDLORD', 'ADMIN')")
    public ResponseEntity<ApiResponse<ApplicationResponse>> getApplication(
            @PathVariable Long applicationId,
            HttpServletRequest httpRequest) {
        ApplicationResponse application = applicationService.getApplicationById(applicationId);
        ApiResponse<ApplicationResponse> response = ApiResponse.success(application);
        response.setPath(httpRequest.getRequestURI());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get my applications", description = "Tenants can view their own applications")
    @GetMapping("/my-applications")
    @PreAuthorize("hasAnyRole('TENANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> getMyApplications(
            HttpServletRequest httpRequest) {
        List<ApplicationResponse> applications = applicationService.getMyApplications();
        ApiResponse<List<ApplicationResponse>> response = ApiResponse.success(applications);
        response.setPath(httpRequest.getRequestURI());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get applications for my properties", description = "Landlords can view applications for their properties")
    @GetMapping("/my-properties")
    @PreAuthorize("hasAnyRole('LANDLORD', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> getApplicationsForMyProperties(
            HttpServletRequest httpRequest) {
        List<ApplicationResponse> applications = applicationService.getApplicationsForMyProperties();
        ApiResponse<List<ApplicationResponse>> response = ApiResponse.success(applications);
        response.setPath(httpRequest.getRequestURI());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get applications by property", description = "Get all applications for a specific property")
    @GetMapping("/property/{propertyId}")
    @PreAuthorize("hasAnyRole('LANDLORD', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> getApplicationsByProperty(
            Authentication authentication,
            @PathVariable Long propertyId,
            HttpServletRequest httpRequest) {
        String callerEmail = authentication.getName();
        List<ApplicationResponse> applications = applicationService.getApplicationsByProperty(propertyId, callerEmail);
        ApiResponse<List<ApplicationResponse>> response = ApiResponse.success(applications);
        response.setPath(httpRequest.getRequestURI());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get applications by status", description = "Filter applications by status (role-based)")
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('TENANT', 'LANDLORD', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> getApplicationsByStatus(
            Authentication authentication,
            @PathVariable RentalApplicationStatus status,
            HttpServletRequest httpRequest) {
        String callerEmail = authentication.getName();
        List<ApplicationResponse> applications = applicationService.getApplicationsByStatus(status, callerEmail);
        ApiResponse<List<ApplicationResponse>> response = ApiResponse.success(applications);
        response.setPath(httpRequest.getRequestURI());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update application status", description = "Landlords can approve/reject applications")
    @PutMapping("/{applicationId}/status")
    @PreAuthorize("hasAnyRole('LANDLORD', 'ADMIN')")
    public ResponseEntity<ApiResponse<ApplicationResponse>> updateApplicationStatus(
            Authentication authentication,
            @PathVariable Long applicationId,
            @RequestBody ApplicationStatusUpdateRequest request,
            HttpServletRequest httpRequest) {
        String callerEmail = authentication.getName();
        ApplicationResponse application = applicationService.updateApplicationStatus(applicationId, request, callerEmail);
        ApiResponse<ApplicationResponse> response = ApiResponse.success(
                "Application status updated successfully",
                application
        );
        response.setPath(httpRequest.getRequestURI());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancel application", description = "Tenants can cancel their pending applications")
    @PutMapping("/{applicationId}/cancel")
    @PreAuthorize("hasAnyRole('TENANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> cancelApplication(
            @PathVariable Long applicationId,
            HttpServletRequest httpRequest) {
        applicationService.cancelApplication(applicationId);
        ApiResponse<Void> response = ApiResponse.success("Application cancelled successfully");
        response.setPath(httpRequest.getRequestURI());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete application", description = "Admin can delete applications")
    @DeleteMapping("/{applicationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteApplication(
            @PathVariable Long applicationId,
            HttpServletRequest httpRequest) {
        applicationService.deleteApplication(applicationId);
        ApiResponse<Void> response = ApiResponse.success("Application deleted successfully");
        response.setPath(httpRequest.getRequestURI());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get application count by status", description = "Get statistics of applications")
    @GetMapping("/count/status/{status}")
    @PreAuthorize("hasAnyRole('LANDLORD', 'ADMIN')")
    public ResponseEntity<ApiResponse<Long>> getApplicationCountByStatus(
            @PathVariable RentalApplicationStatus status,
            HttpServletRequest httpRequest) {
        long count = applicationService.getApplicationCountByStatus(status);
        ApiResponse<Long> response = ApiResponse.success(count);
        response.setPath(httpRequest.getRequestURI());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all applications", description = "Admin can view all applications")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> getAllApplications(
            HttpServletRequest httpRequest) {
        List<ApplicationResponse> applications = applicationService.getAllApplications();
        ApiResponse<List<ApplicationResponse>> response = ApiResponse.success(applications);
        response.setPath(httpRequest.getRequestURI());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get pending applications", description = "Get all pending applications")
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('LANDLORD', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> getPendingApplications(
            HttpServletRequest httpRequest) {
        List<ApplicationResponse> applications = applicationService.getPendingApplications();
        ApiResponse<List<ApplicationResponse>> response = ApiResponse.success(applications);
        response.setPath(httpRequest.getRequestURI());
        return ResponseEntity.ok(response);
    }
}