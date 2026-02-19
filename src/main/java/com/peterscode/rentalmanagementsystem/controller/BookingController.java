package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.model.booking.Booking;
import com.peterscode.rentalmanagementsystem.security.SecurityUser;
import com.peterscode.rentalmanagementsystem.service.booking.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "APIs for tenant booking management")
public class BookingController {

    private final BookingService bookingService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('TENANT')")
    @Operation(summary = "Get my active booking")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyBooking(Authentication authentication) {
        Long tenantId = getUserId(authentication);
        Optional<Booking> booking = bookingService.getActiveBooking(tenantId);

        if (booking.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success("Active booking found", toMap(booking.get())));
        }
        return ResponseEntity.ok(ApiResponse.success("No active booking", null));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Get all bookings (admin/landlord)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllBookings() {
        List<Map<String, Object>> bookings = bookingService.findAll()
                .stream()
                .map(this::toMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Bookings retrieved", bookings));
    }

    @GetMapping("/property/{propertyId}/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check if a property is booked")
    public ResponseEntity<ApiResponse<Boolean>> isPropertyBooked(@PathVariable Long propertyId) {
        boolean booked = bookingService.isPropertyBooked(propertyId);
        return ResponseEntity.ok(ApiResponse.success(booked ? "Property is booked" : "Property is available", booked));
    }

    private Long getUserId(Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return securityUser.user().getId();
    }

    private Map<String, Object> toMap(Booking booking) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", booking.getId());
        map.put("tenantId", booking.getTenant().getId());
        map.put("tenantName", booking.getTenant().getFirstName() + " " + booking.getTenant().getLastName());
        map.put("tenantEmail", booking.getTenant().getEmail());
        map.put("propertyId", booking.getProperty().getId());
        map.put("propertyTitle", booking.getProperty().getTitle());
        map.put("status", booking.getStatus().name());
        map.put("depositPaid", booking.isDepositPaid());
        map.put("rentPaid", booking.isRentPaid());
        map.put("startDate", booking.getStartDate());
        map.put("expiryDate", booking.getExpiryDate());
        map.put("paymentDeadline", booking.getPaymentDeadline());
        return map;
    }
}

