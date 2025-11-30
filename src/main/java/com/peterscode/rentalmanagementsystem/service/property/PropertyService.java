package com.peterscode.rentalmanagementsystem.service.property;

import com.peterscode.rentalmanagementsystem.dto.request.PropertyRequest;
import com.peterscode.rentalmanagementsystem.dto.response.PropertyResponse;
import com.peterscode.rentalmanagementsystem.model.property.Property;

import java.util.List;

public interface PropertyService {


    PropertyResponse createProperty(PropertyRequest request);

    PropertyResponse getPropertyById(Long propertyId);

    List<PropertyResponse> getAllProperties();


    List<PropertyResponse> getPropertiesByOwner(Long ownerId);


    PropertyResponse updateProperty(Long propertyId, PropertyRequest request);

    void deleteProperty(Long propertyId);


    List<Property> getPropertiesByUser(Long userId);
}