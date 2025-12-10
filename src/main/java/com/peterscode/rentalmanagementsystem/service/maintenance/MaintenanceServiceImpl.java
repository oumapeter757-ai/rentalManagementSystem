package com.peterscode.rentalmanagementsystem.service.maintenance;

import com.peterscode.rentalmanagementsystem.dto.request.MaintenanceRequestDto;
import com.peterscode.rentalmanagementsystem.dto.request.MaintenanceUpdateDto;
import com.peterscode.rentalmanagementsystem.dto.response.MaintenanceResponse;
import com.peterscode.rentalmanagementsystem.dto.response.MaintenanceSummaryResponse;
import com.peterscode.rentalmanagementsystem.exception.BadRequestException;
import com.peterscode.rentalmanagementsystem.exception.ResourceNotFoundException;
import com.peterscode.rentalmanagementsystem.model.maintenance.*;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.MaintenanceImageRepository;
import com.peterscode.rentalmanagementsystem.repository.MaintenanceRequestRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import com.peterscode.rentalmanagementsystem.service.MaintenanceService;

import com.peterscode.rentalmanagementsystem.util.FileStorageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenanceServiceImpl implements MaintenanceService {

    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final MaintenanceImageRepository maintenanceImageRepository;
    private final UserRepository userRepository;
    private final FileStorageUtil fileStorageUtil;

    private final List<MaintenanceStatus> OPEN_STATUSES = Arrays.asList(
            MaintenanceStatus.PENDING, MaintenanceStatus.REVIEWED,
            MaintenanceStatus.APPROVED, MaintenanceStatus.IN_PROGRESS,
            MaintenanceStatus.ON_HOLD
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_FILE_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp","image/heic", "image/heif"
    );

    @Override
    @Transactional
    public MaintenanceResponse createMaintenanceRequest(MaintenanceRequestDto requestDto, String tenantEmail) {
        User tenant = userRepository.findByEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validateTenantRole(tenant);

        MaintenanceRequest request = MaintenanceRequest.builder()
                .tenant(tenant)
                .category(requestDto.getCategory())
                .title(requestDto.getTitle())
                .description(requestDto.getDescription())
                .priority(requestDto.getPriority())
                .status(MaintenanceStatus.PENDING)
                .notes(requestDto.getNotes())
                .build();

        request = maintenanceRequestRepository.save(request);

        if (requestDto.getImages() != null && !requestDto.getImages().isEmpty()) {
            List<MaintenanceImage> images = uploadAndCreateImages(requestDto.getImages(), request, tenantEmail);
            request.setImages(images);
            maintenanceRequestRepository.save(request);
        }

        log.info("Maintenance request created: {} by tenant: {}", request.getId(), tenantEmail);
        return mapToResponse(request);
    }

    private void validateTenantRole(User tenant) {
        if (!tenant.getRole().name().contains("TENANT")) {
            throw new BadRequestException("Only tenants can submit maintenance requests");
        }
    }

    @Override
    @Transactional
    public MaintenanceResponse addImagesToRequest(Long requestId, List<MultipartFile> images, String callerEmail) {
        MaintenanceRequest request = maintenanceRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found"));

        if (!isRequestAccessible(requestId, callerEmail)) {
            throw new AccessDeniedException("You don't have permission to modify this request");
        }

        if (images != null && !images.isEmpty()) {
            List<MaintenanceImage> newImages = uploadAndCreateImages(images, request, callerEmail);
            request.getImages().addAll(newImages);
            request = maintenanceRequestRepository.save(request);
        }

        return mapToResponse(request);
    }

    private List<MaintenanceImage> uploadAndCreateImages(List<MultipartFile> files, MaintenanceRequest request, String uploadedBy) {
        List<MaintenanceImage> images = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                validateImageFile(file);

                String imageUrl = fileStorageUtil.storeMaintenanceImage(file, request.getId(), uploadedBy);
                String thumbnailUrl = fileStorageUtil.generateThumbnailUrl(imageUrl, 300, 300);

                MaintenanceImage image = MaintenanceImage.builder()
                        .imageUrl(imageUrl)
                        .thumbnailUrl(thumbnailUrl)
                        .caption("Maintenance request image")
                        .uploadedAt(LocalDateTime.now())
                        .uploadedBy(uploadedBy)
                        .maintenanceRequest(request)
                        .build();

                MaintenanceImage savedImage = maintenanceImageRepository.save(image);
                images.add(savedImage);

                log.info("Image uploaded for maintenance request {}: {}", request.getId(), imageUrl);

            } catch (IOException e) {
                log.error("Failed to upload image for maintenance request {}: {}", request.getId(), e.getMessage());
                throw new BadRequestException("Failed to upload image: " + e.getMessage());
            }
        }

        return images;
    }
    private void validateImageFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }

        if (!fileStorageUtil.isValidImageFile(file)) {
            throw new BadRequestException("Invalid file type. Only images are allowed");
        }

        if (!fileStorageUtil.isValidFileSize(file, MAX_FILE_SIZE)) {
            throw new BadRequestException("File size exceeds maximum limit of 5MB");
        }

        String contentType = file.getContentType();
        log.info("Received file: {}, Content-Type: {}, Size: {}",
                file.getOriginalFilename(), contentType, file.getSize());

        if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType)) {
            log.warn("File type {} not allowed. Allowed types: {}", contentType, ALLOWED_FILE_TYPES);
            throw new BadRequestException("File type not allowed. Allowed types: JPEG, PNG, GIF, WEBP");
        }
    }

    @Override
    public MaintenanceResponse getMaintenanceRequestById(Long id, String callerEmail) {
        MaintenanceRequest request = maintenanceRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found"));

        if (!isRequestAccessible(id, callerEmail)) {
            throw new AccessDeniedException("You don't have permission to view this request");
        }

        return mapToResponse(request);
    }

    @Override
    public List<MaintenanceResponse> getAllMaintenanceRequests(String callerEmail) {
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<MaintenanceRequest> requests = getRequestsBasedOnRole(user);

        return requests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private List<MaintenanceRequest> getRequestsBasedOnRole(User user) {
        if (user.getRole().name().contains("ADMIN")) {
            return maintenanceRequestRepository.findAllByOrderByRequestDateDesc();
        } else if (user.getRole().name().contains("LANDLORD")) {
            // Landlord can see requests for their properties
            // This needs property relationship implementation
            // For now, return empty or implement property-based filtering
            return new ArrayList<>();
        } else {
            // Tenant can only see their own requests
            return maintenanceRequestRepository.findByTenantOrderByRequestDateDesc(user);
        }
    }

    @Override
    public List<MaintenanceResponse> getMyMaintenanceRequests(String tenantEmail) {
        User tenant = userRepository.findByEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<MaintenanceRequest> requests = maintenanceRequestRepository.findByTenantOrderByRequestDateDesc(tenant);

        return requests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<MaintenanceResponse> getRequestsByStatus(String status, String callerEmail) {
        MaintenanceStatus maintenanceStatus = parseMaintenanceStatus(status);
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<MaintenanceRequest> requests = getRequestsByStatusAndRole(maintenanceStatus, user);

        return requests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private List<MaintenanceRequest> getRequestsByStatusAndRole(MaintenanceStatus status, User user) {
        if (user.getRole().name().contains("ADMIN") || user.getRole().name().contains("LANDLORD")) {
            return maintenanceRequestRepository.findByStatusOrderByRequestDateDesc(status);
        } else {
            return maintenanceRequestRepository.findByTenantAndStatusOrderByRequestDateDesc(user, status);
        }
    }

    private MaintenanceStatus parseMaintenanceStatus(String status) {
        try {
            return MaintenanceStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + status);
        }
    }

    @Override
    public List<MaintenanceResponse> getRequestsByCategory(String category, String callerEmail) {
        MaintenanceCategory maintenanceCategory = parseMaintenanceCategory(category);
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<MaintenanceRequest> requests = getRequestsByCategoryAndRole(maintenanceCategory, user);

        return requests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private List<MaintenanceRequest> getRequestsByCategoryAndRole(MaintenanceCategory category, User user) {
        if (user.getRole().name().contains("ADMIN") || user.getRole().name().contains("LANDLORD")) {
            return maintenanceRequestRepository.findByCategoryOrderByRequestDateDesc(category);
        } else {
            return maintenanceRequestRepository.findByTenantAndCategoryOrderByRequestDateDesc(user, category);
        }
    }

    private MaintenanceCategory parseMaintenanceCategory(String category) {
        try {
            return MaintenanceCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid category: " + category);
        }
    }

    @Override
    public List<MaintenanceResponse> getRequestsByPriority(String priority, String callerEmail) {
        MaintenancePriority maintenancePriority = parseMaintenancePriority(priority);
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<MaintenanceRequest> requests = getRequestsByPriorityAndRole(maintenancePriority, user);

        return requests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private List<MaintenanceRequest> getRequestsByPriorityAndRole(MaintenancePriority priority, User user) {
        if (user.getRole().name().contains("ADMIN") || user.getRole().name().contains("LANDLORD")) {
            return maintenanceRequestRepository.findByPriorityOrderByRequestDateDesc(priority);
        } else {
            return maintenanceRequestRepository.findByTenantAndPriorityOrderByRequestDateDesc(user, priority);
        }
    }

    private MaintenancePriority parseMaintenancePriority(String priority) {
        try {
            return MaintenancePriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid priority: " + priority);
        }
    }

    @Override
    public List<MaintenanceResponse> getOpenRequests(String callerEmail) {
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<MaintenanceRequest> requests = getOpenRequestsBasedOnRole(user);

        return requests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private List<MaintenanceRequest> getOpenRequestsBasedOnRole(User user) {
        if (user.getRole().name().contains("ADMIN") || user.getRole().name().contains("LANDLORD")) {
            return maintenanceRequestRepository.findOpenRequests(OPEN_STATUSES);
        } else {
            return maintenanceRequestRepository.findOpenRequestsByTenant(user, OPEN_STATUSES);
        }
    }

    @Override
    @Transactional
    public MaintenanceResponse updateMaintenanceRequest(Long id, MaintenanceUpdateDto updateDto, String callerEmail) {
        MaintenanceRequest request = maintenanceRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found"));

        if (!canUpdateRequest(id, callerEmail)) {
            throw new AccessDeniedException("You don't have permission to update this request");
        }

        updateRequestFields(request, updateDto, callerEmail);

        if (updateDto.getNewImages() != null && !updateDto.getNewImages().isEmpty()) {
            List<MaintenanceImage> newImages = uploadAndCreateImages(updateDto.getNewImages(), request, callerEmail);
            request.getImages().addAll(newImages);
        }

        request = maintenanceRequestRepository.save(request);
        log.info("Maintenance request {} updated by {}", id, callerEmail);

        return mapToResponse(request);
    }

    private void updateRequestFields(MaintenanceRequest request, MaintenanceUpdateDto updateDto, String callerEmail) {
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Update fields
        if (updateDto.getNotes() != null) {
            request.setNotes(updateDto.getNotes());
        }

        if (updateDto.getStatus() != null) {
            request.setStatus(updateDto.getStatus());
        }

        if (updateDto.getAssignedToId() != null) {
            // If you have assignedTo field, implement assignment logic
            // User assignedTo = userRepository.findById(updateDto.getAssignedToId())
            //         .orElseThrow(() -> new ResourceNotFoundException("Assigned user not found"));
            // request.setAssignedTo(assignedTo);
        }
    }

    @Override
    @Transactional
    public MaintenanceResponse updateStatus(Long id, String status, String notes, String callerEmail) {
        MaintenanceRequest request = maintenanceRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found"));

        if (!canUpdateRequest(id, callerEmail)) {
            throw new AccessDeniedException("You don't have permission to update this request");
        }

        MaintenanceStatus newStatus = parseMaintenanceStatus(status);
        request.setStatus(newStatus);

        if (notes != null && !notes.trim().isEmpty()) {
            String timestamp = "[" + LocalDateTime.now() + " - Status Update] ";
            String updatedNotes = request.getNotes() != null ?
                    request.getNotes() + "\n" + timestamp + notes : timestamp + notes;
            request.setNotes(updatedNotes);
        }

        request = maintenanceRequestRepository.save(request);
        log.info("Maintenance request {} status updated to {} by {}", id, status, callerEmail);

        return mapToResponse(request);
    }

    @Override
    @Transactional
    public MaintenanceResponse assignRequest(Long requestId, Long staffId, String callerEmail) {
        MaintenanceRequest request = maintenanceRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found"));

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found"));

        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!caller.getRole().name().contains("ADMIN") && !caller.getRole().name().contains("LANDLORD")) {
            throw new AccessDeniedException("Only admins or landlords can assign requests");
        }

        // Check if staff has appropriate role (e.g., STAFF, MAINTENANCE)
        if (!staff.getRole().name().contains("STAFF") && !staff.getRole().name().contains("MAINTENANCE")) {
            throw new BadRequestException("Cannot assign to user without staff/maintenance role");
        }

        // If you have an assignedTo field in MaintenanceRequest entity, uncomment:
        // request.setAssignedTo(staff);
        // request.setStatus(MaintenanceStatus.IN_PROGRESS);

        String assignmentNote = String.format("Assigned to %s (%s) by %s",
                staff.getFirstName() + " " + staff.getLastName(), staff.getEmail(), callerEmail);
        request.setNotes(request.getNotes() != null ?
                request.getNotes() + "\n" + assignmentNote : assignmentNote);

        request = maintenanceRequestRepository.save(request);
        log.info("Maintenance request {} assigned to {} by {}", requestId, staffId, callerEmail);

        return mapToResponse(request);
    }

    @Override
    @Transactional
    public MaintenanceResponse addNote(Long requestId, String note, String callerEmail) {
        MaintenanceRequest request = maintenanceRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found"));

        if (!isRequestAccessible(requestId, callerEmail)) {
            throw new AccessDeniedException("You don't have permission to add notes to this request");
        }

        String timestamp = "[" + LocalDateTime.now() + "] ";
        String updatedNotes = request.getNotes() != null ?
                request.getNotes() + "\n" + timestamp + note : timestamp + note;

        request.setNotes(updatedNotes);
        request = maintenanceRequestRepository.save(request);

        return mapToResponse(request);
    }

    @Override
    @Transactional
    public void deleteMaintenanceRequest(Long id, String callerEmail) {
        MaintenanceRequest request = maintenanceRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found"));

        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!canDeleteRequest(request, user)) {
            throw new AccessDeniedException("You don't have permission to delete this request");
        }

        if (request.getStatus() != MaintenanceStatus.PENDING) {
            throw new BadRequestException("Can only delete requests with PENDING status");
        }

        // Delete associated images from storage
        deleteRequestImages(request);

        maintenanceRequestRepository.delete(request);

        // Delete maintenance directory
        fileStorageUtil.deleteMaintenanceDirectory(id);

        log.info("Maintenance request {} deleted by {}", id, callerEmail);
    }

    private boolean canDeleteRequest(MaintenanceRequest request, User user) {
        return request.getTenant().getEmail().equals(user.getEmail()) ||
                user.getRole().name().contains("ADMIN");
    }

    @Override
    @Transactional
    public void deleteImage(Long requestId, Long imageId, String callerEmail) {
        MaintenanceRequest request = maintenanceRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found"));

        if (!isRequestAccessible(requestId, callerEmail)) {
            throw new AccessDeniedException("You don't have permission to delete images from this request");
        }

        MaintenanceImage image = maintenanceImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        // Verify image belongs to the request
        if (!image.getMaintenanceRequest().getId().equals(requestId)) {
            throw new BadRequestException("Image does not belong to this maintenance request");
        }

        // Delete file from storage
        boolean deleted = fileStorageUtil.deleteFile(image.getImageUrl());
        if (deleted && image.getThumbnailUrl() != null) {
            fileStorageUtil.deleteFile(image.getThumbnailUrl());
        }

        // Delete from database
        maintenanceImageRepository.delete(image);

        // Remove from request's image list
        request.getImages().removeIf(img -> img.getId().equals(imageId));
        maintenanceRequestRepository.save(request);

        log.info("Image {} deleted from maintenance request {} by {}", imageId, requestId, callerEmail);
    }

    private void deleteRequestImages(MaintenanceRequest request) {
        if (request.getImages() != null) {
            for (MaintenanceImage image : request.getImages()) {
                fileStorageUtil.deleteFile(image.getImageUrl());
                if (image.getThumbnailUrl() != null) {
                    fileStorageUtil.deleteFile(image.getThumbnailUrl());
                }
            }
            maintenanceImageRepository.deleteAll(request.getImages());
        }
    }

    @Override
    public MaintenanceSummaryResponse getMaintenanceSummary(String callerEmail) {
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getRole().name().contains("ADMIN") && !user.getRole().name().contains("LANDLORD")) {
            throw new AccessDeniedException("Only admins or landlords can view maintenance summary");
        }

        List<MaintenanceRequest> allRequests = maintenanceRequestRepository.findAll();

        return buildMaintenanceSummary(allRequests);
    }

    private MaintenanceSummaryResponse buildMaintenanceSummary(List<MaintenanceRequest> allRequests) {
        long totalRequests = allRequests.size();
        long openRequests = allRequests.stream()
                .filter(r -> r.getStatus().isOpen())
                .count();
        long completedRequests = allRequests.stream()
                .filter(r -> r.getStatus().isCompleted())
                .count();
        long urgentRequests = allRequests.stream()
                .filter(r -> r.getPriority() == MaintenancePriority.URGENT ||
                        r.getPriority() == MaintenancePriority.EMERGENCY)
                .count();

        // Requests by category
        Map<String, Long> requestsByCategory = Arrays.stream(MaintenanceCategory.values())
                .collect(Collectors.toMap(
                        MaintenanceCategory::name,
                        category -> allRequests.stream()
                                .filter(r -> r.getCategory() == category)
                                .count()
                ));

        // Requests by status
        Map<String, Long> requestsByStatus = Arrays.stream(MaintenanceStatus.values())
                .collect(Collectors.toMap(
                        MaintenanceStatus::name,
                        status -> allRequests.stream()
                                .filter(r -> r.getStatus() == status)
                                .count()
                ));

        // Requests by priority
        Map<String, Long> requestsByPriority = Arrays.stream(MaintenancePriority.values())
                .collect(Collectors.toMap(
                        MaintenancePriority::name,
                        priority -> allRequests.stream()
                                .filter(r -> r.getPriority() == priority)
                                .count()
                ));

        // Recent requests (last 7 days)
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long requestsLast7Days = allRequests.stream()
                .filter(r -> r.getRequestDate().isAfter(weekAgo))
                .count();

        // Last 30 days
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);
        long requestsLast30Days = allRequests.stream()
                .filter(r -> r.getRequestDate().isAfter(monthAgo))
                .count();

        return MaintenanceSummaryResponse.builder()
                .totalRequests(totalRequests)
                .openRequests(openRequests)
                .completedRequests(completedRequests)
                .urgentRequests(urgentRequests)
                .requestsByCategory(requestsByCategory)
                .requestsByStatus(requestsByStatus)
                .requestsByPriority(requestsByPriority)
                .requestsLast7Days(requestsLast7Days)
                .requestsLast30Days(requestsLast30Days)
                .build();
    }

    @Override
    public MaintenanceSummaryResponse getTenantMaintenanceSummary(String tenantEmail) {
        User tenant = userRepository.findByEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<MaintenanceRequest> tenantRequests = maintenanceRequestRepository.findByTenantOrderByRequestDateDesc(tenant);

        return buildTenantMaintenanceSummary(tenantRequests);
    }

    private MaintenanceSummaryResponse buildTenantMaintenanceSummary(List<MaintenanceRequest> tenantRequests) {
        long totalRequests = tenantRequests.size();
        long openRequests = tenantRequests.stream()
                .filter(r -> r.getStatus().isOpen())
                .count();
        long completedRequests = tenantRequests.stream()
                .filter(r -> r.getStatus().isCompleted())
                .count();
        long urgentRequests = tenantRequests.stream()
                .filter(r -> r.getPriority() == MaintenancePriority.URGENT ||
                        r.getPriority() == MaintenancePriority.EMERGENCY)
                .count();

        return MaintenanceSummaryResponse.builder()
                .totalRequests(totalRequests)
                .openRequests(openRequests)
                .completedRequests(completedRequests)
                .urgentRequests(urgentRequests)
                .build();
    }

    @Override
    public Long getOpenRequestsCount(String callerEmail) {
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole().name().contains("ADMIN") || user.getRole().name().contains("LANDLORD")) {
            return maintenanceRequestRepository.countOpenRequests(OPEN_STATUSES);
        } else {
            return maintenanceRequestRepository.countOpenRequestsByTenant(user, OPEN_STATUSES);
        }
    }

    @Override
    public boolean isRequestAccessible(Long requestId, String userEmail) {
        MaintenanceRequest request = maintenanceRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole().name().contains("ADMIN")) {
            return true;
        }

        if (request.getTenant().getEmail().equals(userEmail)) {
            return true;
        }

        // Add landlord property check if needed
        return false;
    }

    @Override
    public boolean canUpdateRequest(Long requestId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole().name().contains("ADMIN")) {
            return true;
        }

        MaintenanceRequest request = maintenanceRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found"));

        // Landlord update logic (add property check if needed)
        if (user.getRole().name().contains("LANDLORD")) {
            return false; // Implement property ownership check
        }

        // Tenant can only update their own pending requests
        return request.getTenant().getEmail().equals(userEmail) &&
                request.getStatus() == MaintenanceStatus.PENDING;
    }

    private MaintenanceResponse mapToResponse(MaintenanceRequest request) {
        String timeSinceRequest = calculateTimeSinceRequest(request.getRequestDate());

        MaintenanceResponse.MaintenanceResponseBuilder responseBuilder = MaintenanceResponse.builder()
                .id(request.getId())
                .category(request.getCategory())
                .categoryDisplayName(request.getCategory().getDisplayName())
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .priorityDisplayName(request.getPriority().getDisplayName())
                .status(request.getStatus())
                .statusDisplayName(request.getStatus().getDisplayName())
                .notes(request.getNotes())
                .requestDate(request.getRequestDate())
                .updatedAt(LocalDateTime.now())
                .tenantId(request.getTenant().getId())
                .tenantName(request.getTenant().getFirstName() + " " + request.getTenant().getLastName())
                .tenantEmail(request.getTenant().getEmail())

                .isOpen(request.getStatus().isOpen())
                .isCompleted(request.getStatus() == MaintenanceStatus.COMPLETED ||
                        request.getStatus() == MaintenanceStatus.CANCELLED ||
                        request.getStatus() == MaintenanceStatus.REJECTED)
                .daysSinceRequest(calculateDaysSinceRequest(request.getRequestDate()))
                .timeSinceRequest(calculateTimeSinceRequest(request.getRequestDate()))
                .isUrgent(request.getPriority() == MaintenancePriority.URGENT ||
                        request.getPriority() == MaintenancePriority.EMERGENCY);

        // Map images if they exist
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            List<MaintenanceResponse.MaintenanceImageResponse> imageResponses = request.getImages().stream()
                    .map(this::mapImageToResponse)
                    .collect(Collectors.toList());
            responseBuilder.images(imageResponses);
        }

        return responseBuilder.build();
    }

    private MaintenanceResponse.MaintenanceImageResponse mapImageToResponse(MaintenanceImage image) {
        return MaintenanceResponse.MaintenanceImageResponse.builder()
                .id(image.getId())
                .imageUrl(image.getImageUrl())
                .thumbnailUrl(image.getThumbnailUrl())
                .caption(image.getCaption())
                .uploadedAt(image.getUploadedAt())
                .uploadedBy(image.getUploadedBy())
                .build();
    }

    private String calculateDaysSinceRequest(LocalDateTime requestDate) {
        if (requestDate == null) return "N/A";
        long days = Duration.between(requestDate, LocalDateTime.now()).toDays();
        return days + " day" + (days != 1 ? "s" : "");
    }

    private String calculateTimeSinceRequest(LocalDateTime requestDate) {
        if (requestDate == null) return "N/A";

        Duration duration = Duration.between(requestDate, LocalDateTime.now());
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0) {
            return days + "d " + hours + "h ago";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m ago";
        } else if (minutes > 0) {
            return minutes + " minutes ago";
        } else {
            return "Just now";
        }
    }
}