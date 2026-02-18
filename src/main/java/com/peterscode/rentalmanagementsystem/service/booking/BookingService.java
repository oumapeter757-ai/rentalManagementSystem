package com.peterscode.rentalmanagementsystem.service.booking;

import com.peterscode.rentalmanagementsystem.exception.ResourceNotFoundException;
import com.peterscode.rentalmanagementsystem.model.booking.Booking;
import com.peterscode.rentalmanagementsystem.model.booking.BookingStatus;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.BookingRepository;
import com.peterscode.rentalmanagementsystem.repository.PropertyRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    /**
     * Called when a DEPOSIT is paid successfully.
     * Creates a new booking and locks the property.
     */
    public Booking createBookingFromDeposit(Long tenantId, Long propertyId) {
        log.info("Creating booking for tenant {} on property {}", tenantId, propertyId);

        // check if tenant already has an active booking
        if (bookingRepository.existsByTenantIdAndStatus(tenantId, BookingStatus.ACTIVE)) {
            throw new IllegalStateException("Tenant already has an active booking. Cannot book another property.");
        }

        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

        // Check if property is already booked/occupied
        if (!property.isAvailable()) {
            throw new IllegalStateException("Property is not available");
        }

        // Lock property - this prevents race conditions if transaction is atomic
        property.setAvailable(false);
        propertyRepository.save(property);

        Booking booking = Booking.builder()
                .tenant(tenant)
                .property(property)
                .status(BookingStatus.ACTIVE)
                .depositPaid(true)
                .rentPaid(false) // Rent is still pending
                .startDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusMonths(1)) // 1 month duration
                .paymentDeadline(LocalDate.now().plusDays(15)) // 15 days to pay full rent
                .build();

        return bookingRepository.save(booking);
    }

    /**
     * Called when RENT or FULL amount is paid.
     * Marks rent as paid.
     */
    public void completeBookingPayment(Long tenantId, Long propertyId) {
        log.info("Completing payment for tenant {} on property {}", tenantId, propertyId);

        Booking booking = bookingRepository.findByTenantIdAndStatus(tenantId, BookingStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active booking found for this tenant"));

        if (!booking.getProperty().getId().equals(propertyId)) {
            throw new IllegalStateException("Payment mismatch: Booking is for a different property");
        }

        booking.setRentPaid(true);
        // We keep status as ACTIVE until expiry? Or create Lease?
        // For now, keeping as ACTIVE to represent the current month's occupancy.
        bookingRepository.save(booking);
    }

    /**
     * Get active booking for tenant
     */
    @Transactional(readOnly = true)
    public Optional<Booking> getActiveBooking(Long tenantId) {
        return bookingRepository.findByTenantIdAndStatus(tenantId, BookingStatus.ACTIVE);
    }

    /**
     * Check if a property has an active booking
     */
    @Transactional(readOnly = true)
    public boolean isPropertyBooked(Long propertyId) {
        return bookingRepository.findByPropertyIdAndStatus(propertyId, BookingStatus.ACTIVE).isPresent();
    }

    public List<Booking> findAll() {
        return bookingRepository.findAll();
    }
}
