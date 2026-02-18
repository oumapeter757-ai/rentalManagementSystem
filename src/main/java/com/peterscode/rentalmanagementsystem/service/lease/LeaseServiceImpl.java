package com.peterscode.rentalmanagementsystem.service.lease;

import com.peterscode.rentalmanagementsystem.dto.request.LeaseCreateRequest;
import com.peterscode.rentalmanagementsystem.dto.response.LeaseResponse;

import com.peterscode.rentalmanagementsystem.exception.CustomException;
import com.peterscode.rentalmanagementsystem.exception.UserNotFoundException;
import com.peterscode.rentalmanagementsystem.model.lease.Lease;
import com.peterscode.rentalmanagementsystem.model.lease.LeaseMapper;
import com.peterscode.rentalmanagementsystem.model.lease.LeaseStatus;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.LeaseRepository;
import com.peterscode.rentalmanagementsystem.repository.PropertyRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaseServiceImpl implements LeaseService {

    private final LeaseRepository leaseRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;

    @Override
    @Transactional
    public LeaseResponse createLease(String callerEmail, LeaseCreateRequest request) {
        User caller = userRepository.findByEmailIgnoreCase(callerEmail)
                .orElseThrow(() -> new UserNotFoundException("Caller not found"));

        // Only property owner (landlord) or admin can create a lease for a property
        Property property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new CustomException("Property not found"));

        boolean isOwner = property.getOwner() != null && property.getOwner().getId().equals(caller.getId());
        boolean isAdmin = caller.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new CustomException("Only the property owner or admin can create leases");
        }

        // tenant exists
        User tenant = userRepository.findById(request.getTenantId())
                .orElseThrow(() -> new CustomException("Tenant not found"));

        // Validate dates
        if (request.getEndDate().isBefore(request.getStartDate()) || request.getEndDate().isEqual(request.getStartDate())) {
            throw new CustomException("endDate must be after startDate");
        }

        Lease lease = Lease.builder()
                .tenant(tenant)
                .property(property)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .monthlyRent(request.getMonthlyRent())
                .deposit(request.getDeposit() == null ? request.getMonthlyRent() : request.getDeposit())
                .status(LeaseStatus.ACTIVE)
                .notes(request.getNotes())
                .build();

        leaseRepository.save(lease);
        return LeaseMapper.toResponse(lease);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaseResponse getById(Long id) {
        Lease lease = leaseRepository.findById(id)
                .orElseThrow(() -> new CustomException("Lease not found"));
        return LeaseMapper.toResponse(lease);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaseResponse> getByTenant(String callerEmail) {
        User tenant = userRepository.findByEmailIgnoreCase(callerEmail)
                .orElseThrow(() -> new CustomException("Tenant not found"));

        return leaseRepository.findByTenant_Id(tenant.getId())
                .stream().map(LeaseMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaseResponse> getByProperty(Long propertyId, String callerEmail) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new CustomException("Property not found"));

        User caller = userRepository.findByEmailIgnoreCase(callerEmail)
                .orElseThrow(() -> new CustomException("Caller not found"));

        boolean isOwner = property.getOwner() != null && property.getOwner().getId().equals(caller.getId());
        boolean isAdmin = caller.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new CustomException("Access denied");
        }

        return leaseRepository.findByProperty_Id(propertyId)
                .stream().map(LeaseMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LeaseResponse terminateLease(Long id, String callerEmail, String reason) {
        Lease lease = leaseRepository.findById(id)
                .orElseThrow(() -> new CustomException("Lease not found"));

        Property property = lease.getProperty();

        User caller = userRepository.findByEmailIgnoreCase(callerEmail)
                .orElseThrow(() -> new CustomException("Caller not found"));

        boolean isOwner = property.getOwner() != null && property.getOwner().getId().equals(caller.getId());
        boolean isAdmin = caller.getRole() == Role.ADMIN;
        boolean isTenant = lease.getTenant().getId().equals(caller.getId());


        if (!isOwner && !isAdmin && !isTenant) {
            throw new CustomException("Access denied");
        }

        lease.setStatus(LeaseStatus.TERMINATED);
        String existingNotes = lease.getNotes() == null ? "" : lease.getNotes() + "\n";
        lease.setNotes(existingNotes + "Terminated by " + caller.getEmail() + (reason != null ? (": " + reason) : ""));

        leaseRepository.save(lease);
        return LeaseMapper.toResponse(lease);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaseResponse> getActiveLeasesForProperty(Long propertyId) {
        return leaseRepository.findByProperty_IdAndStatus(propertyId, LeaseStatus.ACTIVE)
                .stream().map(LeaseMapper::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<LeaseResponse> getAllLeases(String callerEmail) {
        User caller = userRepository.findByEmailIgnoreCase(callerEmail)
                .orElseThrow(() -> new UserNotFoundException("Caller not found"));

        // Admin sees all leases
        if (caller.getRole() == Role.ADMIN) {
            return leaseRepository.findAll()
                    .stream()
                    .map(LeaseMapper::toResponse)
                    .collect(Collectors.toList());
        }

        // Landlord sees only leases for their properties
        if (caller.getRole() == Role.LANDLORD) {
            return leaseRepository.findAll()
                    .stream()
                    .filter(lease -> lease.getProperty().getOwner() != null && lease.getProperty().getOwner().getId().equals(caller.getId()))
                    .map(LeaseMapper::toResponse)
                    .collect(Collectors.toList());
        }

        // Other roles see no leases
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaseResponse> getMyLeases(String callerEmail) {
        User caller = userRepository.findByEmailIgnoreCase(callerEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Get leases where the caller is the tenant
        return leaseRepository.findAll()
                .stream()
                .filter(lease -> lease.getTenant().getId().equals(caller.getId()))
                .map(LeaseMapper::toResponse)
                .collect(Collectors.toList());
    }
}
