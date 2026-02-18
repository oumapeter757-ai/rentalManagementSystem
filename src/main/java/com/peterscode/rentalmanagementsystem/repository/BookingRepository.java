package com.peterscode.rentalmanagementsystem.repository;

import com.peterscode.rentalmanagementsystem.model.booking.Booking;
import com.peterscode.rentalmanagementsystem.model.booking.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Find active booking for a tenant
    Optional<Booking> findByTenantIdAndStatus(Long tenantId, BookingStatus status);

    // Find active booking for a property
    Optional<Booking> findByPropertyIdAndStatus(Long propertyId, BookingStatus status);

    // Check if tenant has any active booking
    boolean existsByTenantIdAndStatus(Long tenantId, BookingStatus status);

    // Find expired bookings
    @Query("SELECT b FROM Booking b WHERE b.status = :status AND b.expiryDate < :date")
    List<Booking> findExpiredBookings(@Param("status") BookingStatus status, @Param("date") LocalDate date);

    // Find bookings nearing payment deadline
    @Query("SELECT b FROM Booking b WHERE b.status = :status AND b.rentPaid = false AND b.paymentDeadline BETWEEN :startDate AND :endDate")
    List<Booking> findPendingPaymentsNearDeadline(
            @Param("status") BookingStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
