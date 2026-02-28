package com.peterscode.rentalmanagementsystem.service;

import com.peterscode.rentalmanagementsystem.dto.request.ApplicationRequest;
import com.peterscode.rentalmanagementsystem.dto.request.ApplicationStatusUpdateRequest;
import com.peterscode.rentalmanagementsystem.dto.response.ApplicationResponse;
import com.peterscode.rentalmanagementsystem.model.application.RentalApplication;
import com.peterscode.rentalmanagementsystem.model.application.RentalApplicationStatus;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.property.PropertyType;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.PropertyRepository;
import com.peterscode.rentalmanagementsystem.repository.RentalApplicationRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import com.peterscode.rentalmanagementsystem.service.rentalApplication.ApplicationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationServiceImpl Tests")
class ApplicationServiceImplTest {

    @Mock
    private RentalApplicationRepository applicationRepository;
    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ApplicationServiceImpl applicationService;

    private User tenant;
    private User landlord;
    private User admin;
    private Property property;

    @BeforeEach
    void setUp() {
        tenant = User.builder().id(1L).email("tenant@test.com").username("tenant")
                .password("enc").firstName("John").lastName("Doe").role(Role.TENANT).build();

        landlord = User.builder().id(2L).email("landlord@test.com").username("landlord")
                .password("enc").firstName("Jane").lastName("Smith").role(Role.LANDLORD).build();

        admin = User.builder().id(3L).email("admin@test.com").username("admin")
                .password("enc").firstName("Admin").lastName("User").role(Role.ADMIN).build();

        property = Property.builder().id(10L).title("Nice Apartment").location("Nairobi")
                .address("456 Main St").rentAmount(BigDecimal.valueOf(30000))
                .type(PropertyType.APARTMENT).bedrooms(2).bathrooms(1).furnished(true)
                .available(true).owner(landlord).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));
        lenient().when(userRepository.findByEmailIgnoreCase(user.getEmail()))
                .thenReturn(Optional.of(user));
    }

    // ── createApplication ────────────────────────────────────────────────

    @Test
    @DisplayName("createApplication - success")
    void createApplication_success() {
        setAuthenticatedUser(tenant);
        ApplicationRequest request = new ApplicationRequest();
        request.setPropertyId(10L);
        request.setReason("Looking for new place");

        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));
        when(applicationRepository.findByPropertyIdAndTenantId(10L, 1L)).thenReturn(Optional.empty());
        when(applicationRepository.save(any(RentalApplication.class))).thenAnswer(inv -> {
            RentalApplication app = inv.getArgument(0);
            app.setId(100L);
            app.setCreatedAt(LocalDateTime.now());
            app.setUpdatedAt(LocalDateTime.now());
            return app;
        });

        ApplicationResponse result = applicationService.createApplication(request);

        assertThat(result).isNotNull();
        assertThat(result.getTenantId()).isEqualTo(1L);
        assertThat(result.getPropertyTitle()).isEqualTo("Nice Apartment");
        assertThat(result.getStatus()).isEqualTo(RentalApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("createApplication - non-tenant denied")
    void createApplication_nonTenantDenied() {
        setAuthenticatedUser(landlord);
        ApplicationRequest request = new ApplicationRequest();
        request.setPropertyId(10L);

        assertThatThrownBy(() -> applicationService.createApplication(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Only tenants");
    }

    @Test
    @DisplayName("createApplication - property not available")
    void createApplication_propertyNotAvailable() {
        setAuthenticatedUser(tenant);
        property.setAvailable(false);
        ApplicationRequest request = new ApplicationRequest();
        request.setPropertyId(10L);

        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));

        assertThatThrownBy(() -> applicationService.createApplication(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("createApplication - duplicate application prevented")
    void createApplication_duplicatePrevented() {
        setAuthenticatedUser(tenant);
        ApplicationRequest request = new ApplicationRequest();
        request.setPropertyId(10L);

        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));
        when(applicationRepository.findByPropertyIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(RentalApplication.builder().build()));

        assertThatThrownBy(() -> applicationService.createApplication(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already applied");
    }

    // ── getApplicationsByProperty ────────────────────────────────────────

    @Test
    @DisplayName("getApplicationsByProperty - owner can access")
    void getApplicationsByProperty_ownerAccess() {
        when(userRepository.findByEmailIgnoreCase("landlord@test.com")).thenReturn(Optional.of(landlord));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));
        when(applicationRepository.findByPropertyId(10L)).thenReturn(List.of(
                RentalApplication.builder().id(1L).tenant(tenant).property(property)
                        .status(RentalApplicationStatus.PENDING)
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build()
        ));

        List<ApplicationResponse> result = applicationService.getApplicationsByProperty(10L, "landlord@test.com");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getApplicationsByProperty - access denied for non-owner")
    void getApplicationsByProperty_accessDenied() {
        User otherLandlord = User.builder().id(99L).email("other@test.com").role(Role.LANDLORD).build();
        when(userRepository.findByEmailIgnoreCase("other@test.com")).thenReturn(Optional.of(otherLandlord));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));

        assertThatThrownBy(() -> applicationService.getApplicationsByProperty(10L, "other@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    // ── updateApplicationStatus ──────────────────────────────────────────

    @Test
    @DisplayName("updateApplicationStatus - approve by owner")
    void updateApplicationStatus_approveByOwner() {
        RentalApplication app = RentalApplication.builder()
                .id(100L).tenant(tenant).property(property)
                .status(RentalApplicationStatus.PENDING)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        ApplicationStatusUpdateRequest request = new ApplicationStatusUpdateRequest();
        request.setStatus(RentalApplicationStatus.APPROVED);

        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));
        when(userRepository.findByEmailIgnoreCase("landlord@test.com")).thenReturn(Optional.of(landlord));
        when(applicationRepository.save(any(RentalApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplicationResponse result = applicationService.updateApplicationStatus(100L, request, "landlord@test.com");

        assertThat(result.getStatus()).isEqualTo(RentalApplicationStatus.APPROVED);
    }

    @Test
    @DisplayName("updateApplicationStatus - cannot update cancelled")
    void updateApplicationStatus_cannotUpdateCancelled() {
        RentalApplication app = RentalApplication.builder()
                .id(100L).tenant(tenant).property(property)
                .status(RentalApplicationStatus.CANCELLED)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        ApplicationStatusUpdateRequest request = new ApplicationStatusUpdateRequest();
        request.setStatus(RentalApplicationStatus.APPROVED);

        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));
        when(userRepository.findByEmailIgnoreCase("landlord@test.com")).thenReturn(Optional.of(landlord));

        assertThatThrownBy(() -> applicationService.updateApplicationStatus(100L, request, "landlord@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cancelled");
    }

    // ── cancelApplication ────────────────────────────────────────────────

    @Test
    @DisplayName("cancelApplication - success")
    void cancelApplication_success() {
        setAuthenticatedUser(tenant);
        RentalApplication app = RentalApplication.builder()
                .id(100L).tenant(tenant).property(property)
                .status(RentalApplicationStatus.PENDING).build();

        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));

        applicationService.cancelApplication(100L);

        assertThat(app.getStatus()).isEqualTo(RentalApplicationStatus.CANCELLED);
        verify(applicationRepository).save(app);
    }

    @Test
    @DisplayName("cancelApplication - only pending can be cancelled")
    void cancelApplication_notPending() {
        setAuthenticatedUser(tenant);
        RentalApplication app = RentalApplication.builder()
                .id(100L).tenant(tenant).property(property)
                .status(RentalApplicationStatus.APPROVED).build();

        when(applicationRepository.findById(100L)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> applicationService.cancelApplication(100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Only pending");
    }

    // ── getApplicationCountByStatus ──────────────────────────────────────

    @Test
    @DisplayName("getApplicationCountByStatus - returns count")
    void getApplicationCountByStatus_returnsCount() {
        when(applicationRepository.countByStatus(RentalApplicationStatus.PENDING)).thenReturn(5L);

        long count = applicationService.getApplicationCountByStatus(RentalApplicationStatus.PENDING);

        assertThat(count).isEqualTo(5L);
    }

    // ── getAllApplications ───────────────────────────────────────────────

    @Test
    @DisplayName("getAllApplications - admin sees all")
    void getAllApplications_adminSeesAll() {
        setAuthenticatedUser(admin);

        RentalApplication app = RentalApplication.builder()
                .id(1L).tenant(tenant).property(property)
                .status(RentalApplicationStatus.PENDING)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(applicationRepository.findAll()).thenReturn(List.of(app));

        List<ApplicationResponse> result = applicationService.getAllApplications();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getAllApplications - non-admin denied")
    void getAllApplications_nonAdminDenied() {
        setAuthenticatedUser(tenant);

        assertThatThrownBy(() -> applicationService.getAllApplications())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Only admin");
    }
}
