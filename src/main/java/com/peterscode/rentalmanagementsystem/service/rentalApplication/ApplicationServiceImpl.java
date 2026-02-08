package com.peterscode.rentalmanagementsystem.service.rentalApplication;

import com.peterscode.rentalmanagementsystem.dto.request.ApplicationRequest;
import com.peterscode.rentalmanagementsystem.dto.request.ApplicationStatusUpdateRequest;
import com.peterscode.rentalmanagementsystem.dto.response.ApplicationResponse;
import com.peterscode.rentalmanagementsystem.model.application.RentalApplication;
import com.peterscode.rentalmanagementsystem.model.application.RentalApplicationStatus;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.RentalApplicationRepository;
import com.peterscode.rentalmanagementsystem.repository.PropertyRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final RentalApplicationRepository applicationRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ApplicationResponse createApplication(ApplicationRequest request) {
        User currentUser = getCurrentAuthenticatedUser();

        // Ensure only tenants can apply
        if (currentUser.getRole() != Role.TENANT) {
            throw new RuntimeException("Only tenants can create rental applications");
        }

        Property property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + request.getPropertyId()));

        // Check if property is available
        if (!property.getAvailable()) {
            throw new RuntimeException("Property is not available for rent");
        }

        // Check if user already applied for this property
        applicationRepository.findByPropertyIdAndTenantId(request.getPropertyId(), currentUser.getId())
                .ifPresent(app -> {
                    throw new RuntimeException("You have already applied for this property");
                });

        RentalApplication application = RentalApplication.builder()
                .tenant(currentUser)
                .property(property)
                .status(RentalApplicationStatus.PENDING)
                .reason(request.getReason())
                .build();

        RentalApplication savedApplication = applicationRepository.save(application);
        return mapToResponse(savedApplication);
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationResponse getApplicationById(Long applicationId) {
        RentalApplication application = getApplication(applicationId);
        User currentUser = getCurrentAuthenticatedUser();

        // Check access: tenant, property owner, or admin
        if (!application.getTenant().getId().equals(currentUser.getId()) &&
                !application.getProperty().getOwner().getId().equals(currentUser.getId()) &&
                currentUser.getRole() != Role.ADMIN) {
            throw new RuntimeException("Access denied to this application");
        }

        return mapToResponse(application);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationResponse> getMyApplications() {
        User currentUser = getCurrentAuthenticatedUser();
        List<RentalApplication> applications = applicationRepository.findByTenant(currentUser);
        return applications.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationResponse> getApplicationsForMyProperties() {
        User currentUser = getCurrentAuthenticatedUser();
        List<RentalApplication> applications = applicationRepository.findByPropertyOwner(currentUser);
        return applications.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationResponse> getApplicationsByProperty(Long propertyId, String callerEmail) {
        User currentUser = userRepository.findByEmailIgnoreCase(callerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found"));

        // Check if current user is the property owner or admin
        if (!property.getOwner().getId().equals(currentUser.getId()) &&
                currentUser.getRole() != Role.ADMIN) {
            throw new RuntimeException("Access denied to applications for this property");
        }

        List<RentalApplication> applications = applicationRepository.findByPropertyId(propertyId);
        return applications.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationResponse> getApplicationsByStatus(RentalApplicationStatus status, String callerEmail) {
        User currentUser = userRepository.findByEmailIgnoreCase(callerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<RentalApplication> applications;

        if (currentUser.getRole() == Role.ADMIN) {
            applications = applicationRepository.findByStatus(status);
        } else if (currentUser.getRole() == Role.LANDLORD) {
            applications = applicationRepository.findByOwnerAndStatus(currentUser, status);
        } else {
            applications = applicationRepository.findByTenantAndStatus(currentUser, status);
        }

        return applications.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public ApplicationResponse updateApplicationStatus(Long applicationId, ApplicationStatusUpdateRequest request, String callerEmail) {
        RentalApplication application = getApplication(applicationId);
        User currentUser = userRepository.findByEmailIgnoreCase(callerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Only property owner or admin can update status
        if (!application.getProperty().getOwner().getId().equals(currentUser.getId()) &&
                currentUser.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only property owner or admin can update application status");
        }

        // Cannot update cancelled applications
        if (application.getStatus() == RentalApplicationStatus.CANCELLED) {
            throw new RuntimeException("Cannot update status of cancelled application");
        }

        application.setStatus(request.getStatus());

        RentalApplication updatedApplication = applicationRepository.save(application);
        return mapToResponse(updatedApplication);
    }

    @Override
    @Transactional
    public void cancelApplication(Long applicationId) {
        RentalApplication application = getApplication(applicationId);
        User currentUser = getCurrentAuthenticatedUser();

        // Only tenant can cancel their own application
        if (!application.getTenant().getId().equals(currentUser.getId())) {
            throw new RuntimeException("You can only cancel your own applications");
        }

        // Only pending applications can be cancelled
        if (application.getStatus() != RentalApplicationStatus.PENDING) {
            throw new RuntimeException("Only pending applications can be cancelled");
        }

        application.setStatus(RentalApplicationStatus.CANCELLED);
        applicationRepository.save(application);
    }

    @Override
    @Transactional
    public void deleteApplication(Long applicationId) {
        RentalApplication application = getApplication(applicationId);
        User currentUser = getCurrentAuthenticatedUser();

        // Only admin can delete applications
        if (currentUser.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only admin can delete applications");
        }

        applicationRepository.delete(application);
    }

    @Override
    @Transactional(readOnly = true)
    public long getApplicationCountByStatus(RentalApplicationStatus status) {
        return applicationRepository.countByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationResponse> getAllApplications() {
        User currentUser = getCurrentAuthenticatedUser();

        if (currentUser.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only admin can view all applications");
        }

        List<RentalApplication> applications = applicationRepository.findAll();
        return applications.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationResponse> getPendingApplications() {
        User currentUser = getCurrentAuthenticatedUser();
        return getApplicationsByStatus(RentalApplicationStatus.PENDING, currentUser.getEmail());
    }

    // Helper methods
    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + username));
    }

    private RentalApplication getApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found with id: " + applicationId));
    }

    private ApplicationResponse mapToResponse(RentalApplication application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .tenantId(application.getTenant().getId())
                .tenantName(application.getTenant().getFirstName() + " " + application.getTenant().getLastName())
                .tenantEmail(application.getTenant().getEmail())
                .propertyId(application.getProperty().getId())
                .propertyTitle(application.getProperty().getTitle())
                .propertyAddress(application.getProperty().getAddress())
                .propertyRent(application.getProperty().getRentAmount().doubleValue())
                .status(application.getStatus())
                .reason(application.getReason())
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }
}