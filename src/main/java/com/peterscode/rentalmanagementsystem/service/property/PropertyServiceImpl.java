package com.peterscode.rentalmanagementsystem.service.property;

import com.peterscode.rentalmanagementsystem.dto.request.PropertyRequest;
import com.peterscode.rentalmanagementsystem.dto.response.PropertyResponse;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.property.PropertyImage;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.PropertyRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public PropertyResponse createProperty(PropertyRequest request) {

        User currentUser = getCurrentAuthenticatedUser();

        if (currentUser.getRole() == Role.TENANT) {
            throw new RuntimeException("TENANTS cannot create properties");
        }


        User owner = currentUser;


        if (currentUser.getRole() == Role.ADMIN && request.getOwnerId() != null) {
            owner = getUserById(request.getOwnerId());
            if (owner.getRole() != Role.LANDLORD && owner.getRole() != Role.ADMIN) {
                throw new RuntimeException("Owner must be a LANDLORD or ADMIN");
            }
        }

        Property property = Property.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .address(request.getAddress())
                .location(request.getAddress())
                .rentAmount(BigDecimal.valueOf(request.getRent()))
                .type(request.getType())
                .bedrooms(request.getBedrooms())
                .bathrooms(request.getBathrooms())
                .furnished(request.getFurnished())
                .size(request.getSize())
                .available(true)
                .amenities(request.getAmenities())
                .owner(owner)
                .build();

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            List<PropertyImage> images = request.getImageUrls().stream()
                    .map(url -> PropertyImage.builder()
                            .fileUrl(url)
                            .property(property)
                            .build())
                    .toList();
            property.setImages(images);
        }

        propertyRepository.save(property);
        return mapToResponse(property);
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyResponse getPropertyById(Long propertyId) {
        return mapToResponse(getProperty(propertyId));
    }

    public List<Property> getPropertiesByUser(Long userId) {
        User user = getUserById(userId);
        return propertyRepository.findByOwner(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertyResponse> getAllProperties() {
        return propertyRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertyResponse> getPropertiesByOwner(Long ownerId) {
        User owner = getUserById(ownerId);

        if (owner.getRole() != Role.LANDLORD && owner.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only landlords and admin have properties");
        }

        return owner.getProperties()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public PropertyResponse updateProperty(Long propertyId, PropertyRequest request) {
        User currentUser = getCurrentAuthenticatedUser();
        Property property = getProperty(propertyId);

        if (currentUser.getRole() == Role.LANDLORD &&
                !property.getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("LANDLORD cannot update properties they do NOT own");
        }

        if (currentUser.getRole() == Role.TENANT) {
            throw new RuntimeException("TENANT cannot update properties");
        }

        property.setTitle(request.getTitle());
        property.setDescription(request.getDescription());
        property.setAddress(request.getAddress());
        property.setLocation(request.getAddress());
        property.setRentAmount(BigDecimal.valueOf(request.getRent()));
        property.setType(request.getType());
        property.setBedrooms(request.getBedrooms());
        property.setBathrooms(request.getBathrooms());
        property.setFurnished(request.getFurnished());
        property.setSize(request.getSize());
        property.setAmenities(request.getAmenities());
        property.setAvailable(request.getAvailable() != null ? request.getAvailable() : true);


        if (property.getImages() != null) {
            property.getImages().clear();
        }

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            property.setImages(
                    request.getImageUrls().stream()
                            .map(url -> PropertyImage.builder()
                                    .fileUrl(url)
                                    .property(property)
                                    .build())
                            .toList()
            );
        }

        propertyRepository.save(property);
        return mapToResponse(property);
    }

    @Override
    @Transactional
    public void deleteProperty(Long propertyId) {
        User currentUser = getCurrentAuthenticatedUser();
        Property property = getProperty(propertyId);

        if (currentUser.getRole() == Role.LANDLORD &&
                !property.getOwner().getId().equals(currentUser.getId())) {
            throw new RuntimeException("LANDLORD cannot delete properties they do NOT own");
        }

        if (currentUser.getRole() == Role.TENANT) {
            throw new RuntimeException("TENANT cannot delete properties");
        }

        propertyRepository.delete(property);
    }


    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + username));
    }


    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
    }



    private Property getProperty(Long propertyId) {
        return propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found with ID: " + propertyId));
    }

    private PropertyResponse mapToResponse(Property property) {
        return PropertyResponse.builder()
                .id(property.getId())
                .title(property.getTitle())
                .description(property.getDescription())
                .address(property.getAddress())
                .location(property.getLocation())
                .rent(property.getRentAmount().doubleValue())
                .type(property.getType())
                .bedrooms(property.getBedrooms())
                .bathrooms(property.getBathrooms())
                .furnished(property.getFurnished())
                .available(property.getAvailable())
                .amenities(property.getAmenities())
                .size(property.getSize())
                .ownerId(String.valueOf(property.getOwner().getId()))
                .ownerEmail(property.getOwner().getEmail())
                .imageUrls(property.getImages() != null ?
                        property.getImages().stream()
                                .map(PropertyImage::getFileUrl)
                                .toList() :
                        List.of())
                .build();
    }
}