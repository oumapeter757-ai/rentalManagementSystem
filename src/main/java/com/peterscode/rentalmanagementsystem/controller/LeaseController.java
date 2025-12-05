package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.request.LeaseCreateRequest;
import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.dto.response.LeaseResponse;
import com.peterscode.rentalmanagementsystem.service.lease.LeaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leases")
@RequiredArgsConstructor
public class LeaseController {

    private final LeaseService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('LANDLORD','ADMIN')")
    public ResponseEntity<ApiResponse<LeaseResponse>> create(
            Authentication authentication,
            @Valid @RequestBody LeaseCreateRequest request
    ) {
        String callerEmail = authentication.getName();
        LeaseResponse resp = service.createLease(callerEmail, request);

        return ResponseEntity.ok(
                ApiResponse.ok("Lease created successfully", resp)
        );
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<ApiResponse<List<LeaseResponse>>> myLeases(
            Authentication authentication
    ) {
        String callerEmail = authentication.getName();
        List<LeaseResponse> list = service.getByTenant(callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Fetched my leases", list)
        );
    }

    @GetMapping("/property/{propertyId}")
    @PreAuthorize("hasAnyRole('LANDLORD','ADMIN')")
    public ResponseEntity<ApiResponse<List<LeaseResponse>>> forProperty(
            Authentication authentication,
            @PathVariable Long propertyId
    ) {
        String callerEmail = authentication.getName();
        List<LeaseResponse> list = service.getByProperty(propertyId, callerEmail);

        return ResponseEntity.ok(
                ApiResponse.ok("Fetched leases for property", list)
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<LeaseResponse>> get(
            @PathVariable Long id
    ) {
        LeaseResponse resp = service.getById(id);
        return ResponseEntity.ok(
                ApiResponse.ok("Lease fetched", resp)
        );
    }

    @PutMapping("/{id}/terminate")
    @PreAuthorize("hasAnyRole('LANDLORD','ADMIN','TENANT')")
    public ResponseEntity<ApiResponse<LeaseResponse>> terminate(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        String callerEmail = authentication.getName();
        LeaseResponse resp = service.terminateLease(id, callerEmail, reason);

        return ResponseEntity.ok(
                ApiResponse.ok("Lease terminated", resp)
        );
    }

    @GetMapping("/property/{propertyId}/active")
    @PreAuthorize("hasAnyRole('LANDLORD','ADMIN')")
    public ResponseEntity<ApiResponse<List<LeaseResponse>>> activeForProperty(
            @PathVariable Long propertyId
    ) {
        List<LeaseResponse> list = service.getActiveLeasesForProperty(propertyId);

        return ResponseEntity.ok(
                ApiResponse.ok("Active leases for property", list)
        );
    }
}
