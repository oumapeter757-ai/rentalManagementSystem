package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.request.PropertyRequest;
import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.dto.response.PropertyResponse;
import com.peterscode.rentalmanagementsystem.dto.response.PublicPropertyResponse;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.service.property.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;


    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @PostMapping("/create")
    public ResponseEntity<PropertyResponse> createProperty(@RequestBody PropertyRequest request) {
        PropertyResponse property = propertyService.createProperty(request);
        return ResponseEntity.ok(property);
    }


    @PreAuthorize("permitAll()")
    @GetMapping("/property/{propertyId}")
    public ResponseEntity<PropertyResponse> getProperty(@PathVariable Long propertyId) {
        PropertyResponse property = propertyService.getPropertyById(propertyId);
        return ResponseEntity.ok(property);
    }


    // PUBLIC ENDPOINT - Get all properties WITHOUT owner information
    @PreAuthorize("permitAll()")
    @GetMapping("/properties")
    public ResponseEntity<List<PublicPropertyResponse>> getAllPublicProperties() {
        List<PublicPropertyResponse> properties = propertyService.getAllPublicProperties();
        return ResponseEntity.ok(properties);
    }

    // ADMIN ENDPOINT - Get all properties WITH owner information
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/all")
    public ResponseEntity<List<PropertyResponse>> getAllPropertiesForAdmin() {
        List<PropertyResponse> properties = propertyService.getAllProperties();
        return ResponseEntity.ok(properties);
    }


    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @GetMapping("/my-properties")
    public ResponseEntity<List<PropertyResponse>> getMyProperties() {

        throw new UnsupportedOperationException("This endpoint needs to be implemented in the service layer");
    }


    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<PropertyResponse>> getPropertiesByOwnerId(@PathVariable Long ownerId) {
        List<PropertyResponse> properties = propertyService.getPropertiesByOwner(ownerId);
        return ResponseEntity.ok(properties);
    }


    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @PutMapping("/{propertyId}")
    public ResponseEntity<PropertyResponse> updateProperty(
            Authentication authentication,
            @PathVariable Long propertyId,
            @RequestBody PropertyRequest request
    ) {
        String callerEmail = authentication.getName();
        PropertyResponse property = propertyService.updateProperty(propertyId, request, callerEmail);
        return ResponseEntity.ok(property);
    }


    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @DeleteMapping("/{propertyId}")
    public ResponseEntity<ApiResponse<Void>> deleteProperty(
            Authentication authentication,
            @PathVariable Long propertyId) {
        String callerEmail = authentication.getName();
        propertyService.deleteProperty(propertyId, callerEmail);
        return ResponseEntity.ok(ApiResponse.success("Property deleted successfully", null));
    }
}