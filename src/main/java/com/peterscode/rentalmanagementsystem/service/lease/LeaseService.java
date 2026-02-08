package com.peterscode.rentalmanagementsystem.service.lease;

import com.peterscode.rentalmanagementsystem.dto.request.LeaseCreateRequest;
import com.peterscode.rentalmanagementsystem.dto.response.LeaseResponse;

import java.util.List;

public interface LeaseService {
    LeaseResponse createLease(String callerEmail, LeaseCreateRequest request);
    LeaseResponse getById(Long id);
    List<LeaseResponse> getByTenant(String callerEmail);
    List<LeaseResponse> getByProperty(Long propertyId, String callerEmail);
    LeaseResponse terminateLease(Long id, String callerEmail, String reason);
    List<LeaseResponse> getActiveLeasesForProperty(Long propertyId);
    List<LeaseResponse> getAllLeases(String callerEmail); // Admin - get all leases, Landlord - get leases for owned properties
}
