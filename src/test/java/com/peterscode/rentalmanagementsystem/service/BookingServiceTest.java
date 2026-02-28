package com.peterscode.rentalmanagementsystem.service;

import com.peterscode.rentalmanagementsystem.exception.ResourceNotFoundException;
import com.peterscode.rentalmanagementsystem.model.booking.Booking;
import com.peterscode.rentalmanagementsystem.model.booking.BookingStatus;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.property.PropertyType;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.BookingRepository;
import com.peterscode.rentalmanagementsystem.repository.PropertyRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import com.peterscode.rentalmanagementsystem.service.booking.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Tests")
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookingService bookingService;

    private User tenant;
    private Property property;

    @BeforeEach
    void setUp() {
        tenant = User.builder()
                .id(1L)
                .email("tenant@test.com")
                .username("tenant1")
                .password("encoded")
                .firstName("John")
                .lastName("Doe")
                .role(Role.TENANT)
                .build();

        property = Property.builder()
                .id(10L)
                .title("Test Apartment")
                .location("Nairobi")
                .address("123 Main St")
                .rentAmount(BigDecimal.valueOf(25000))
                .type(PropertyType.APARTMENT)
                .bedrooms(2)
                .bathrooms(1)
                .furnished(false)
                .available(true)
                .owner(User.builder().id(99L).role(Role.LANDLORD).build())
                .build();
    }

    // ── createBookingFromDeposit ──────────────────────────────────────────

    @Test
    @DisplayName("createBookingFromDeposit - success")
    void createBookingFromDeposit_success() {
        when(bookingRepository.existsByTenantIdAndStatus(1L, BookingStatus.ACTIVE)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(100L);
            return b;
        });

        Booking result = bookingService.createBookingFromDeposit(1L, 10L);

        assertThat(result).isNotNull();
        assertThat(result.getTenant()).isEqualTo(tenant);
        assertThat(result.getProperty()).isEqualTo(property);
        assertThat(result.getStatus()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(result.isDepositPaid()).isTrue();
        assertThat(result.isRentPaid()).isFalse();
        assertThat(result.getStartDate()).isEqualTo(LocalDate.now());
        assertThat(result.getExpiryDate()).isEqualTo(LocalDate.now().plusMonths(1));
        assertThat(result.getPaymentDeadline()).isEqualTo(LocalDate.now().plusDays(15));

        // Property should be marked unavailable
        assertThat(property.getAvailable()).isFalse();
        verify(propertyRepository).save(property);
    }

    @Test
    @DisplayName("createBookingFromDeposit - tenant already has active booking")
    void createBookingFromDeposit_tenantAlreadyHasActiveBooking() {
        when(bookingRepository.existsByTenantIdAndStatus(1L, BookingStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBookingFromDeposit(1L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has an active booking");
    }

    @Test
    @DisplayName("createBookingFromDeposit - tenant not found")
    void createBookingFromDeposit_tenantNotFound() {
        when(bookingRepository.existsByTenantIdAndStatus(1L, BookingStatus.ACTIVE)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBookingFromDeposit(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tenant not found");
    }

    @Test
    @DisplayName("createBookingFromDeposit - property not found")
    void createBookingFromDeposit_propertyNotFound() {
        when(bookingRepository.existsByTenantIdAndStatus(1L, BookingStatus.ACTIVE)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(propertyRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBookingFromDeposit(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Property not found");
    }

    @Test
    @DisplayName("createBookingFromDeposit - property not available")
    void createBookingFromDeposit_propertyNotAvailable() {
        property.setAvailable(false);
        when(bookingRepository.existsByTenantIdAndStatus(1L, BookingStatus.ACTIVE)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));

        assertThatThrownBy(() -> bookingService.createBookingFromDeposit(1L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
    }

    // ── completeBookingPayment ───────────────────────────────────────────

    @Test
    @DisplayName("completeBookingPayment - success")
    void completeBookingPayment_success() {
        Booking booking = Booking.builder()
                .id(100L).tenant(tenant).property(property)
                .status(BookingStatus.ACTIVE).depositPaid(true).rentPaid(false)
                .startDate(LocalDate.now()).expiryDate(LocalDate.now().plusMonths(1))
                .build();

        when(bookingRepository.findByTenantIdAndStatus(1L, BookingStatus.ACTIVE))
                .thenReturn(Optional.of(booking));

        bookingService.completeBookingPayment(1L, 10L);

        assertThat(booking.isRentPaid()).isTrue();
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("completeBookingPayment - no active booking")
    void completeBookingPayment_noActiveBooking() {
        when(bookingRepository.findByTenantIdAndStatus(1L, BookingStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.completeBookingPayment(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("completeBookingPayment - property mismatch")
    void completeBookingPayment_propertyMismatch() {
        Property otherProperty = Property.builder().id(999L).build();
        Booking booking = Booking.builder()
                .id(100L).tenant(tenant).property(otherProperty)
                .status(BookingStatus.ACTIVE).build();

        when(bookingRepository.findByTenantIdAndStatus(1L, BookingStatus.ACTIVE))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.completeBookingPayment(1L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different property");
    }

    // ── getActiveBooking & isPropertyBooked ──────────────────────────────

    @Test
    @DisplayName("getActiveBooking - returns booking when found")
    void getActiveBooking_found() {
        Booking booking = Booking.builder().id(100L).tenant(tenant).build();
        when(bookingRepository.findByTenantIdAndStatus(1L, BookingStatus.ACTIVE))
                .thenReturn(Optional.of(booking));

        Optional<Booking> result = bookingService.getActiveBooking(1L);
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("isPropertyBooked - returns true when active booking exists")
    void isPropertyBooked_true() {
        when(bookingRepository.findByPropertyIdAndStatus(10L, BookingStatus.ACTIVE))
                .thenReturn(Optional.of(Booking.builder().build()));

        assertThat(bookingService.isPropertyBooked(10L)).isTrue();
    }

    @Test
    @DisplayName("isPropertyBooked - returns false when no active booking")
    void isPropertyBooked_false() {
        when(bookingRepository.findByPropertyIdAndStatus(10L, BookingStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(bookingService.isPropertyBooked(10L)).isFalse();
    }

    @Test
    @DisplayName("findAll - returns all bookings")
    void findAll_returnsList() {
        when(bookingRepository.findAll()).thenReturn(List.of(
                Booking.builder().id(1L).build(),
                Booking.builder().id(2L).build()
        ));

        List<Booking> result = bookingService.findAll();
        assertThat(result).hasSize(2);
    }
}
