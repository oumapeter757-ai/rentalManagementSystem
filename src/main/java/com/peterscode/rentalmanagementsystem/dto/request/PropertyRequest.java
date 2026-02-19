package com.peterscode.rentalmanagementsystem.dto.request;

import com.peterscode.rentalmanagementsystem.model.property.PropertyType;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyRequest {

    private String title;

    private String description;

    private String address;

    private double rent;
    private Double depositAmount;
    private Integer bedrooms;

    private PropertyType type;

    private Integer bathrooms;
    private Long ownerId;

    private Boolean furnished;
    private Integer numberOfRooms;

    private Double size;

    private List<String> amenities;

    private List<String> imageUrls;
    @Builder.Default
    private Boolean available = true;

    private List<String> galleryImages;
}
