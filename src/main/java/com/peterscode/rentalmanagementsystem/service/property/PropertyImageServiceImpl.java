package com.peterscode.rentalmanagementsystem.service.property;

import com.peterscode.rentalmanagementsystem.dto.response.PropertyImageResponse;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.property.PropertyImage;
import com.peterscode.rentalmanagementsystem.repository.PropertyImageRepository;
import com.peterscode.rentalmanagementsystem.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PropertyImageServiceImpl implements PropertyImageService {

    private final PropertyImageRepository propertyImageRepository;
    private final PropertyRepository propertyRepository;

    @Override
    @Transactional
    public PropertyImageResponse saveImage(Long propertyId, String imageUrl) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + propertyId));

        PropertyImage image = PropertyImage.builder()
                .fileUrl(imageUrl)
                .property(property)
                .build();

        PropertyImage savedImage = propertyImageRepository.save(image);

        // If this is the first image, set it as main image
        if (propertyImageRepository.countByPropertyId(propertyId) == 1) {
            property.setMainImageUrl(imageUrl);
            propertyRepository.save(property);
        }

        return mapToResponse(savedImage);
    }

    @Override
    @Transactional
    public List<PropertyImageResponse> saveImages(Long propertyId, List<String> imageUrls) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + propertyId));

        List<PropertyImage> images = imageUrls.stream()
                .map(url -> PropertyImage.builder()
                        .fileUrl(url)
                        .property(property)
                        .build())
                .toList();

        List<PropertyImage> savedImages = propertyImageRepository.saveAll(images);


        if (property.getMainImageUrl() == null && !savedImages.isEmpty()) {
            property.setMainImageUrl(savedImages.get(0).getFileUrl());
            propertyRepository.save(property);
        }

        return savedImages.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertyImageResponse> getImagesByPropertyId(Long propertyId) {
        List<PropertyImage> images = propertyImageRepository.findByPropertyId(propertyId);
        return images.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteImagesByPropertyId(Long propertyId) {
        propertyImageRepository.deleteByPropertyId(propertyId);

        // Clear main image URL
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + propertyId));
        property.setMainImageUrl(null);
        propertyRepository.save(property);
    }

    @Override
    @Transactional
    public void deleteImage(Long imageId) {
        PropertyImage image = propertyImageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found with id: " + imageId));

        Property property = image.getProperty();

        // If this image is the main image, clear it
        if (image.getFileUrl().equals(property.getMainImageUrl())) {
            property.setMainImageUrl(null);
            propertyRepository.save(property);
        }

        propertyImageRepository.delete(image);
    }

    @Override
    @Transactional(readOnly = true)
    public long getImageCountByPropertyId(Long propertyId) {
        return propertyImageRepository.countByPropertyId(propertyId);
    }

    // Helper method to map entity to response DTO
    private PropertyImageResponse mapToResponse(PropertyImage image) {
        return PropertyImageResponse.builder()
                .id(image.getId())
                .fileUrl(image.getFileUrl())
                .propertyId(image.getProperty().getId())
                .createdAt(image.getCreatedAt())
                .build();
    }
}