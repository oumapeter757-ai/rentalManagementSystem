package com.peterscode.rentalmanagementsystem.service;

import com.peterscode.rentalmanagementsystem.dto.request.MaintenanceRequestDto;
import com.peterscode.rentalmanagementsystem.dto.response.MaintenanceResponse;
import com.peterscode.rentalmanagementsystem.dto.response.MaintenanceSummaryResponse;
import com.peterscode.rentalmanagementsystem.exception.BadRequestException;
import com.peterscode.rentalmanagementsystem.exception.ResourceNotFoundException;
import com.peterscode.rentalmanagementsystem.model.maintenance.*;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.property.PropertyType;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.MaintenanceImageRepository;
import com.peterscode.rentalmanagementsystem.repository.MaintenanceRequestRepository;
import com.peterscode.rentalmanagementsystem.repository.PropertyRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import com.peterscode.rentalmanagementsystem.service.maintenance.MaintenanceServiceImpl;
import com.peterscode.rentalmanagementsystem.util.FileStorageUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaintenanceServiceImpl Tests")
class MaintenanceServiceImplTest {

    @Mock
    private MaintenanceRequestRepository maintenanceRequestRepository;
    @Mock
    private MaintenanceImageRepository maintenanceImageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private FileStorageUtil fileStorageUtil;

    @InjectMocks
    private MaintenanceServiceImpl maintenanceService;

    private User tenant;
    private User landlord;
    private User admin;
    private Property property;
    private MaintenanceRequest maintenanceRequest;

    @BeforeEach
    void setUp() {
        tenant = User.builder().id(1L).email("tenant@test.com").username("tenant")
                .password("enc").firstName("John").lastName("Doe")
                .role(Role.TENANT).phoneNumber("0712345678").build();

        landlord = User.builder().id(2L).email("landlord@test.com").username("landlord")
                .password("enc").firstName("Jane").lastName("Smith").role(Role.LANDLORD).build();

        admin = User.builder().id(3L).email("admin@test.com").username("admin")
                .password("enc").firstName("Admin").lastName("User").role(Role.ADMIN).build();

        property = Property.builder().id(10L).title("Nice Apartment")
                .location("Nairobi").address("123 St")
                .rentAmount(BigDecimal.valueOf(25000))
                .type(PropertyType.APARTMENT).bedrooms(2).bathrooms(1)
                .furnished(true).available(true).owner(landlord).build();

        maintenanceRequest = MaintenanceRequest.builder()
                .id(100L).tenant(tenant).property(property)
                .category(MaintenanceCategory.PLUMBING)
                .title("Leaking faucet").description("Kitchen faucet is dripping")
                .priority(MaintenancePriority.MEDIUM)
                .status(MaintenanceStatus.PENDING)
                .notes("Started last week")
                .requestDate(LocalDateTime.now())
                .images(new ArrayList<>())
                .build();
    }

    // ── createMaintenanceRequest ─────────────────────────────────────────

    @Test
    @DisplayName("createMaintenanceRequest - tenant success")
    void createMaintenanceRequest_success() {
        MaintenanceRequestDto dto = new MaintenanceRequestDto();
        dto.setPropertyId(10L);
        dto.setCategory(MaintenanceCategory.PLUMBING);
        dto.setTitle("Leaking faucet");
        dto.setDescription("Kitchen faucet dripping");
        dto.setPriority(MaintenancePriority.MEDIUM);

        when(userRepository.findByEmail("tenant@test.com")).thenReturn(Optional.of(tenant));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));
        when(maintenanceRequestRepository.save(any(MaintenanceRequest.class))).thenAnswer(inv -> {
            MaintenanceRequest req = inv.getArgument(0);
            req.setId(100L);
            req.setRequestDate(LocalDateTime.now());
            return req;
        });

        MaintenanceResponse result = maintenanceService.createMaintenanceRequest(dto, "tenant@test.com");

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Leaking faucet");
        assertThat(result.getStatus()).isEqualTo(MaintenanceStatus.PENDING);
        assertThat(result.getTenantName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("createMaintenanceRequest - non-tenant denied")
    void createMaintenanceRequest_nonTenantDenied() {
        MaintenanceRequestDto dto = new MaintenanceRequestDto();
        dto.setPropertyId(10L);

        when(userRepository.findByEmail("landlord@test.com")).thenReturn(Optional.of(landlord));

        assertThatThrownBy(() -> maintenanceService.createMaintenanceRequest(dto, "landlord@test.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only tenants");
    }

    @Test
    @DisplayName("createMaintenanceRequest - user not found")
    void createMaintenanceRequest_userNotFound() {
        MaintenanceRequestDto dto = new MaintenanceRequestDto();
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> maintenanceService.createMaintenanceRequest(dto, "unknown@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getAllMaintenanceRequests role-based ──────────────────────────────

    @Test
    @DisplayName("getAllMaintenanceRequests - admin sees all")
    void getAllMaintenanceRequests_adminSeesAll() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(maintenanceRequestRepository.findAllByOrderByRequestDateDesc())
                .thenReturn(List.of(maintenanceRequest));

        List<MaintenanceResponse> result = maintenanceService.getAllMaintenanceRequests("admin@test.com");

        assertThat(result).hasSize(1);
        verify(maintenanceRequestRepository).findAllByOrderByRequestDateDesc();
    }

    @Test
    @DisplayName("getAllMaintenanceRequests - landlord sees own properties' requests")
    void getAllMaintenanceRequests_landlordFiltered() {
        when(userRepository.findByEmail("landlord@test.com")).thenReturn(Optional.of(landlord));
        when(maintenanceRequestRepository.findByPropertyOwnerOrderByRequestDateDesc(landlord))
                .thenReturn(List.of(maintenanceRequest));

        List<MaintenanceResponse> result = maintenanceService.getAllMaintenanceRequests("landlord@test.com");

        assertThat(result).hasSize(1);
        verify(maintenanceRequestRepository).findByPropertyOwnerOrderByRequestDateDesc(landlord);
    }

    @Test
    @DisplayName("getAllMaintenanceRequests - tenant sees own requests")
    void getAllMaintenanceRequests_tenantOwn() {
        when(userRepository.findByEmail("tenant@test.com")).thenReturn(Optional.of(tenant));
        when(maintenanceRequestRepository.findByTenantOrderByRequestDateDesc(tenant))
                .thenReturn(List.of(maintenanceRequest));

        List<MaintenanceResponse> result = maintenanceService.getAllMaintenanceRequests("tenant@test.com");

        assertThat(result).hasSize(1);
    }

    // ── getRequestsByStatus ─────────────────────────────────────────────

    @Test
    @DisplayName("getRequestsByStatus - valid status")
    void getRequestsByStatus_valid() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(maintenanceRequestRepository.findByStatusOrderByRequestDateDesc(MaintenanceStatus.PENDING))
                .thenReturn(List.of(maintenanceRequest));

        List<MaintenanceResponse> result = maintenanceService.getRequestsByStatus("PENDING", "admin@test.com");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getRequestsByStatus - invalid status")
    void getRequestsByStatus_invalid() {
        assertThatThrownBy(() -> maintenanceService.getRequestsByStatus("INVALID", "admin@test.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid status");
    }

    // ── deleteMaintenanceRequest ─────────────────────────────────────────

    @Test
    @DisplayName("deleteMaintenanceRequest - tenant deletes own pending")
    void deleteMaintenanceRequest_tenantOwn() {
        maintenanceRequest.setStatus(MaintenanceStatus.PENDING);

        when(maintenanceRequestRepository.findById(100L)).thenReturn(Optional.of(maintenanceRequest));
        when(userRepository.findByEmail("tenant@test.com")).thenReturn(Optional.of(tenant));

        maintenanceService.deleteMaintenanceRequest(100L, "tenant@test.com");

        verify(maintenanceRequestRepository).delete(maintenanceRequest);
        verify(fileStorageUtil).deleteMaintenanceDirectory(100L);
    }

    @Test
    @DisplayName("deleteMaintenanceRequest - cannot delete non-PENDING")
    void deleteMaintenanceRequest_notPending() {
        maintenanceRequest.setStatus(MaintenanceStatus.IN_PROGRESS);

        when(maintenanceRequestRepository.findById(100L)).thenReturn(Optional.of(maintenanceRequest));
        when(userRepository.findByEmail("tenant@test.com")).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> maintenanceService.deleteMaintenanceRequest(100L, "tenant@test.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");
    }

    // ── isRequestAccessible ─────────────────────────────────────────────

    @Test
    @DisplayName("isRequestAccessible - admin always true")
    void isRequestAccessible_adminAlwaysTrue() {
        when(maintenanceRequestRepository.findById(100L)).thenReturn(Optional.of(maintenanceRequest));
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        assertThat(maintenanceService.isRequestAccessible(100L, "admin@test.com")).isTrue();
    }

    @Test
    @DisplayName("isRequestAccessible - tenant own request")
    void isRequestAccessible_tenantOwn() {
        when(maintenanceRequestRepository.findById(100L)).thenReturn(Optional.of(maintenanceRequest));
        when(userRepository.findByEmail("tenant@test.com")).thenReturn(Optional.of(tenant));

        assertThat(maintenanceService.isRequestAccessible(100L, "tenant@test.com")).isTrue();
    }

    // ── getMaintenanceSummary ───────────────────────────────────────────

    @Test
    @DisplayName("getMaintenanceSummary - admin gets summary")
    void getMaintenanceSummary_adminSuccess() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(maintenanceRequestRepository.findAll()).thenReturn(List.of(maintenanceRequest));

        MaintenanceSummaryResponse summary = maintenanceService.getMaintenanceSummary("admin@test.com");

        assertThat(summary.getTotalRequests()).isEqualTo(1);
        assertThat(summary.getOpenRequests()).isEqualTo(1); // PENDING is an open status
    }

    @Test
    @DisplayName("getMaintenanceSummary - tenant denied")
    void getMaintenanceSummary_tenantDenied() {
        when(userRepository.findByEmail("tenant@test.com")).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> maintenanceService.getMaintenanceSummary("tenant@test.com"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
