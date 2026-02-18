package com.peterscode.rentalmanagementsystem.dto.response;

import com.peterscode.rentalmanagementsystem.model.property.PropertyType;
import lombok.*;

import java.util.List;

/**
 * Public-facing property response DTO that excludes sensitive owner information.
 * Used for unauthenticated property listings to protect owner privacy.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicPropertyResponse {

    private Long id;
    private String title;
    private String description;
    private String location;
    private String address;
    private double rent;
    private double depositAmount;
    private PropertyType type;

    private Integer bedrooms;
    private Integer bathrooms;
    private Boolean furnished;
    private Boolean available;
    private Double size;
    private List<String> amenities;
    private String mainImageUrl;
    private List<String> imageUrls;

}
