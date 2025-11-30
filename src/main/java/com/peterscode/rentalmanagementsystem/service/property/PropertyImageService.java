package com.peterscode.rentalmanagementsystem.service.property;

import com.peterscode.rentalmanagementsystem.dto.response.PropertyImageResponse;

import java.util.List;

public interface PropertyImageService {

    PropertyImageResponse saveImage(Long propertyId, String imageUrl);

    List<PropertyImageResponse> saveImages(Long propertyId, List<String> imageUrls);

    List<PropertyImageResponse> getImagesByPropertyId(Long propertyId);

    void deleteImagesByPropertyId(Long propertyId);

    void deleteImage(Long imageId);

    long getImageCountByPropertyId(Long propertyId);
}