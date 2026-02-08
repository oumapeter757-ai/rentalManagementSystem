package com.peterscode.rentalmanagementsystem.service.rentalApplication;

import com.peterscode.rentalmanagementsystem.dto.request.ApplicationRequest;
import com.peterscode.rentalmanagementsystem.dto.request.ApplicationStatusUpdateRequest;
import com.peterscode.rentalmanagementsystem.dto.response.ApplicationResponse;
import com.peterscode.rentalmanagementsystem.model.application.RentalApplicationStatus;

import java.util.List;

public interface ApplicationService {

    ApplicationResponse createApplication(ApplicationRequest request);

    ApplicationResponse getApplicationById(Long applicationId);

    List<ApplicationResponse> getMyApplications();

    List<ApplicationResponse> getApplicationsForMyProperties();

    List<ApplicationResponse> getApplicationsByProperty(Long propertyId, String callerEmail);

    List<ApplicationResponse> getApplicationsByStatus(RentalApplicationStatus status, String callerEmail);

    ApplicationResponse updateApplicationStatus(Long applicationId, ApplicationStatusUpdateRequest request, String callerEmail);

    void cancelApplication(Long applicationId);

    void deleteApplication(Long applicationId);

    long getApplicationCountByStatus(RentalApplicationStatus status);

    List<ApplicationResponse> getAllApplications(); // Admin only

    List<ApplicationResponse> getPendingApplications(); // Get all pending applications
}