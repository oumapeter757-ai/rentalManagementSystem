package com.peterscode.rentalmanagementsystem.service;

import com.peterscode.rentalmanagementsystem.dto.request.LeaseCreateRequest;
import com.peterscode.rentalmanagementsystem.dto.response.LeaseResponse;
import com.peterscode.rentalmanagementsystem.exception.CustomException;
import com.peterscode.rentalmanagementsystem.model.lease.Lease;
import com.peterscode.rentalmanagementsystem.model.lease.LeaseStatus;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.property.PropertyType;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.LeaseRepository;
import com.peterscode.rentalmanagementsystem.repository.PropertyRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import com.peterscode.rentalmanagementsystem.service.lease.LeaseServiceImpl;
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
@DisplayName("LeaseServiceImpl Tests")
class LeaseServiceImplTest {

        @Mock
        private LeaseRepository leaseRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private PropertyRepository propertyRepository;

        @InjectMocks
        private LeaseServiceImpl leaseService;

        private User admin;
        private User landlord;
        private User tenant;
        private Property property;
        private LeaseCreateRequest createRequest;

        @BeforeEach
        void setUp() {
                admin = User.builder().id(1L).email("admin@test.com").username("admin")
                                .password("enc").role(Role.ADMIN).build();

                landlord = User.builder().id(2L).email("landlord@test.com").username("landlord")
                                .password("enc").role(Role.LANDLORD).build();

                tenant = User.builder().id(3L).email("tenant@test.com").username("tenant")
                                .password("enc").firstName("John").lastName("Doe").role(Role.TENANT).build();

                property = Property.builder().id(10L).title("Test Apt").location("Nairobi")
                                .address("123 St").rentAmount(BigDecimal.valueOf(25000))
                                .type(PropertyType.APARTMENT).bedrooms(2).bathrooms(1).furnished(false)
                                .available(true).owner(landlord).build();

                createRequest = new LeaseCreateRequest();
                createRequest.setPropertyId(10L);
                createRequest.setTenantId(3L);
                createRequest.setStartDate(LocalDate.now());
                createRequest.setEndDate(LocalDate.now().plusYears(1));
                createRequest.setMonthlyRent(BigDecimal.valueOf(25000));
                createRequest.setNotes("Standard lease");
        }

        // ── createLease ──────────────────────────────────────────────────────

    @Test
    @DisplayName("createLease - by owner (landlord) - success")
    void createLease_byOwner_success() {
        when(userRepository.findByEmailIgnoreCase("landlord@test.com")).thenReturn(Optional.of(landlord));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));
        when(userRepository.findById(3L)).thenReturn(Optional.of(tenant));
        when(leaseRepository.save(any(Lease.class))).thenAnswer(inv -> {
            Lease l = inv.getArgument(0);
            l.setId(100L);
            l.setCreatedAt(LocalDateTime.now());
            l.setUpdatedAt(LocalDateTime.now());
            return l;
        });

        LeaseResponse result = leaseService.createLease("landlord@test.com", createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getTenantFirstName()).isEqualTo("John");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(leaseRepository).save(any(Lease.class));
    }

    @Test
    @DisplayName("createLease - by admin - success")
    void createLease_byAdmin_success() {
        when(userRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(admin));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));
        when(userRepository.findById(3L)).thenReturn(Optional.of(tenant));
        when(leaseRepository.save(any(Lease.class))).thenAnswer(inv -> {
            Lease l = inv.getArgument(0);
            l.setId(101L);
            l.setCreatedAt(LocalDateTime.now());
            l.setUpdatedAt(LocalDateTime.now());
            return l;
        });

        LeaseResponse result = leaseService.createLease("admin@test.com", createRequest);

        assertThat(result).isNotNull();
    }

        @Test
        @DisplayName("createLease - access denied for non-owner")
        void createLease_accessDenied() {
                User otherLandlord = User.builder().id(99L).email("other@test.com").role(Role.LANDLORD).build();
                when(userRepository.findByEmailIgnoreCase("other@test.com")).thenReturn(Optional.of(otherLandlord));
                when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));

                assertThatThrownBy(() -> leaseService.createLease("other@test.com", createRequest))
                                .isInstanceOf(CustomException.class)
                                .hasMessageContaining("Only the property owner or admin");
        }

        @Test
        @DisplayName("createLease - invalid dates (end before start)")
        void createLease_invalidDates() {
                createRequest.setEndDate(LocalDate.now().minusDays(1)); // end before start

                when(userRepository.findByEmailIgnoreCase("landlord@test.com")).thenReturn(Optional.of(landlord));
                when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));
                when(userRepository.findById(3L)).thenReturn(Optional.of(tenant));

                assertThatThrownBy(() -> leaseService.createLease("landlord@test.com", createRequest))
                                .isInstanceOf(CustomException.class)
                                .hasMessageContaining("endDate must be after startDate");
        }

        // ── getById ──────────────────────────────────────────────────────────

        @Test
        @DisplayName("getById - found")
        void getById_found() {
                Lease lease = Lease.builder().id(100L).tenant(tenant).property(property)
                                .startDate(LocalDate.now()).endDate(LocalDate.now().plusYears(1))
                                .monthlyRent(BigDecimal.valueOf(25000)).status(LeaseStatus.ACTIVE)
                                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

                when(leaseRepository.findById(100L)).thenReturn(Optional.of(lease));

                LeaseResponse result = leaseService.getById(100L);

                assertThat(result).isNotNull();
                assertThat(result.getStatus()).isEqualTo("ACTIVE");
        }

    @Test
    @DisplayName("getById - not found")
    void getById_notFound() {
        when(leaseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaseService.getById(999L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("Lease not found");
    }

        // ── terminateLease ──────────────────────────────────────────────────

        @Test
        @DisplayName("terminateLease - by property owner")
        void terminateLease_byOwner() {
                Lease lease = Lease.builder().id(100L).tenant(tenant).property(property)
                                .startDate(LocalDate.now()).endDate(LocalDate.now().plusYears(1))
                                .monthlyRent(BigDecimal.valueOf(25000)).status(LeaseStatus.ACTIVE)
                                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

                when(leaseRepository.findById(100L)).thenReturn(Optional.of(lease));
                when(userRepository.findByEmailIgnoreCase("landlord@test.com")).thenReturn(Optional.of(landlord));
                when(leaseRepository.save(any(Lease.class))).thenAnswer(inv -> inv.getArgument(0));

                LeaseResponse result = leaseService.terminateLease(100L, "landlord@test.com", "Non-payment");

                assertThat(result.getStatus()).isEqualTo("TERMINATED");
        }

        @Test
        @DisplayName("terminateLease - access denied for unrelated user")
        void terminateLease_accessDenied() {
                Lease lease = Lease.builder().id(100L).tenant(tenant).property(property)
                                .startDate(LocalDate.now()).endDate(LocalDate.now().plusYears(1))
                                .monthlyRent(BigDecimal.valueOf(25000)).status(LeaseStatus.ACTIVE)
                                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

                User stranger = User.builder().id(999L).email("stranger@test.com").role(Role.TENANT).build();

                when(leaseRepository.findById(100L)).thenReturn(Optional.of(lease));
                when(userRepository.findByEmailIgnoreCase("stranger@test.com")).thenReturn(Optional.of(stranger));

                assertThatThrownBy(() -> leaseService.terminateLease(100L, "stranger@test.com", "reason"))
                                .isInstanceOf(CustomException.class)
                                .hasMessageContaining("Access denied");
        }

        // ── getAllLeases role-based ───────────────────────────────────────────

        @Test
        @DisplayName("getAllLeases - admin sees all")
        void getAllLeases_asAdmin() {
                Lease l1 = Lease.builder().id(1L).tenant(tenant).property(property)
                                .status(LeaseStatus.ACTIVE).createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now()).startDate(LocalDate.now())
                                .endDate(LocalDate.now().plusYears(1)).monthlyRent(BigDecimal.valueOf(25000)).build();

                when(userRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(admin));
                when(leaseRepository.findAll()).thenReturn(List.of(l1));

                List<LeaseResponse> result = leaseService.getAllLeases("admin@test.com");

                assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getAllLeases - landlord sees only own properties' leases")
        void getAllLeases_asLandlord() {
                Property otherProperty = Property.builder().id(20L)
                                .owner(User.builder().id(999L).build()).build();

                Lease ownLease = Lease.builder().id(1L).tenant(tenant).property(property)
                                .status(LeaseStatus.ACTIVE).createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now()).startDate(LocalDate.now())
                                .endDate(LocalDate.now().plusYears(1)).monthlyRent(BigDecimal.valueOf(25000)).build();

                Lease otherLease = Lease.builder().id(2L).tenant(tenant).property(otherProperty)
                                .status(LeaseStatus.ACTIVE).createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now()).startDate(LocalDate.now())
                                .endDate(LocalDate.now().plusYears(1)).monthlyRent(BigDecimal.valueOf(20000)).build();

                when(userRepository.findByEmailIgnoreCase("landlord@test.com")).thenReturn(Optional.of(landlord));
                when(leaseRepository.findAll()).thenReturn(List.of(ownLease, otherLease));

                List<LeaseResponse> result = leaseService.getAllLeases("landlord@test.com");

                assertThat(result).hasSize(1); // Only sees lease for own property
        }

        @Test
        @DisplayName("getMyLeases - returns tenant's leases")
        void getMyLeases_returnsTenantLeases() {
                Lease lease = Lease.builder().id(1L).tenant(tenant).property(property)
                                .status(LeaseStatus.ACTIVE).createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now()).startDate(LocalDate.now())
                                .endDate(LocalDate.now().plusYears(1)).monthlyRent(BigDecimal.valueOf(25000)).build();

                when(userRepository.findByEmailIgnoreCase("tenant@test.com")).thenReturn(Optional.of(tenant));
                when(leaseRepository.findAll()).thenReturn(List.of(lease));

                List<LeaseResponse> result = leaseService.getMyLeases("tenant@test.com");

                assertThat(result).hasSize(1);
        }
}
