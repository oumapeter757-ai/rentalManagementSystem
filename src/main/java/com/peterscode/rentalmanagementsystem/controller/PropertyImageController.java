package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.response.PropertyImageResponse;
import com.peterscode.rentalmanagementsystem.service.property.PropertyImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyImageController {

    private final PropertyImageService propertyImageService;

    @PostMapping("/{propertyId}/images")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    public ResponseEntity<PropertyImageResponse> addImage(
            @PathVariable Long propertyId,
            @RequestParam String imageUrl) {

        PropertyImageResponse image = propertyImageService.saveImage(propertyId, imageUrl);
        return ResponseEntity.ok(image);
    }

    @PostMapping("/{propertyId}/images/multiple")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    public ResponseEntity<List<PropertyImageResponse>> addMultipleImages(
            @PathVariable Long propertyId,
            @RequestBody List<String> imageUrls) {

        List<PropertyImageResponse> images = propertyImageService.saveImages(propertyId, imageUrls);
        return ResponseEntity.ok(images);
    }

    @GetMapping("/{propertyId}/images")  // Fixed: Added {propertyId} path variable
    @PreAuthorize("permitAll()")
    public ResponseEntity<List<PropertyImageResponse>> getPropertyImages(@PathVariable Long propertyId) {
        List<PropertyImageResponse> images = propertyImageService.getImagesByPropertyId(propertyId);
        return ResponseEntity.ok(images);
    }

    @DeleteMapping("/images/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    public ResponseEntity<Void> deleteImage(

            @PathVariable Long imageId) {

        propertyImageService.deleteImage(imageId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{propertyId}/images")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    public ResponseEntity<Void> deleteAllPropertyImages(@PathVariable Long propertyId) {
        propertyImageService.deleteImagesByPropertyId(propertyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{propertyId}/images/count")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Long> getImageCount(@PathVariable Long propertyId) {
        long count = propertyImageService.getImageCountByPropertyId(propertyId);
        return ResponseEntity.ok(count);
    }
}