package com.peterscode.rentalmanagementsystem.dto.response;

import com.peterscode.rentalmanagementsystem.model.property.PropertyType;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyResponse {

    private Long id;
    private String title;
    private String description;
    private String location;
    private String address;
    private double rent;
    private PropertyType type;

    private Integer bedrooms;
    private Integer bathrooms;
    private Boolean furnished;
    private Boolean available;
    private Double size;
    private List<String> amenities;
    private String mainImageUrl;

    private String ownerId;
    private String ownerEmail;

    private List<String> imageUrls;
}
