package com.peterscode.rentalmanagementsystem.service;

import com.peterscode.rentalmanagementsystem.dto.request.PropertyRequest;
import com.peterscode.rentalmanagementsystem.dto.response.PropertyResponse;
import com.peterscode.rentalmanagementsystem.dto.response.PublicPropertyResponse;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.property.PropertyType;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.PropertyRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import com.peterscode.rentalmanagementsystem.service.audit.AuditLogService;
import com.peterscode.rentalmanagementsystem.service.property.PropertyServiceImpl;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PropertyServiceImpl Tests")
class PropertyServiceImplTest {

    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private PropertyServiceImpl propertyService;

    private User landlord;
    private User admin;
    private User tenant;
    private Property property;

    @BeforeEach
    void setUp() {
        landlord = User.builder().id(1L).email("landlord@test.com").username("landlord")
                .password("enc").firstName("Jane").lastName("Smith").role(Role.LANDLORD).build();

        admin = User.builder().id(2L).email("admin@test.com").username("admin")
                .password("enc").firstName("Admin").lastName("User").role(Role.ADMIN).build();

        tenant = User.builder().id(3L).email("tenant@test.com").username("tenant")
                .password("enc").firstName("John").lastName("Doe").role(Role.TENANT).build();

        property = Property.builder().id(10L).title("Nice Apartment").description("2BR apt")
                .location("Nairobi").address("123 Main St")
                .rentAmount(BigDecimal.valueOf(25000))
                .depositAmount(BigDecimal.valueOf(25000))
                .type(PropertyType.APARTMENT).bedrooms(2).bathrooms(1).furnished(true)
                .available(true).size(75.0).amenities(new ArrayList<>())
                .images(new ArrayList<>()).owner(landlord).build();
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

    // ── createProperty ──────────────────────────────────────────────────

    @Test
    @DisplayName("createProperty - landlord creates own property")
    void createProperty_landlordSuccess() {
        setAuthenticatedUser(landlord);

        PropertyRequest request = PropertyRequest.builder()
                .title("New Apartment").description("Modern 2BR")
                .address("456 Oak St").rent(30000)
                .type(PropertyType.APARTMENT).bedrooms(2).bathrooms(1)
                .furnished(true).size(80.0).amenities(List.of("WiFi", "Parking"))
                .build();

        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> {
            Property p = inv.getArgument(0);
            p.setId(20L);
            return p;
        });

        PropertyResponse result = propertyService.createProperty(request);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("New Apartment");
        assertThat(result.getRent()).isEqualTo(30000.0);
        verify(propertyRepository).save(any(Property.class));
    }

    @Test
    @DisplayName("createProperty - tenant denied")
    void createProperty_tenantDenied() {
        setAuthenticatedUser(tenant);

        PropertyRequest request = PropertyRequest.builder()
                .title("Test").address("123").rent(10000)
                .type(PropertyType.STUDIO).bedrooms(1).bathrooms(1).furnished(false).build();

        assertThatThrownBy(() -> propertyService.createProperty(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("TENANTS cannot create");
    }

    // ── getPropertyById ─────────────────────────────────────────────────

    @Test
    @DisplayName("getPropertyById - found")
    void getPropertyById_found() {
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));

        PropertyResponse result = propertyService.getPropertyById(10L);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Nice Apartment");
        assertThat(result.getOwnerId()).isEqualTo("1");
    }

    @Test
    @DisplayName("getPropertyById - not found")
    void getPropertyById_notFound() {
        when(propertyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> propertyService.getPropertyById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Property not found");
    }

    // ── getAllProperties ─────────────────────────────────────────────────

    @Test
    @DisplayName("getAllProperties - returns list")
    void getAllProperties_returnsList() {
        when(propertyRepository.findAll()).thenReturn(List.of(property));

        List<PropertyResponse> result = propertyService.getAllProperties();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Nice Apartment");
    }

    @Test
    @DisplayName("getAllPublicProperties - returns list without owner info")
    void getAllPublicProperties_returnsList() {
        when(propertyRepository.findAll()).thenReturn(List.of(property));

        List<PublicPropertyResponse> result = propertyService.getAllPublicProperties();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Nice Apartment");
        // PublicPropertyResponse should NOT have owner fields
    }

    // ── updateProperty ──────────────────────────────────────────────────

    @Test
    @DisplayName("updateProperty - by owner landlord")
    void updateProperty_byOwner() {
        when(userRepository.findByEmailIgnoreCase("landlord@test.com")).thenReturn(Optional.of(landlord));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        PropertyRequest request = PropertyRequest.builder()
                .title("Updated Title").description("Updated desc")
                .address("789 New St").rent(35000)
                .type(PropertyType.APARTMENT).bedrooms(3).bathrooms(2)
                .furnished(true).size(90.0).amenities(List.of("Pool"))
                .build();

        PropertyResponse result = propertyService.updateProperty(10L, request, "landlord@test.com");

        assertThat(result.getTitle()).isEqualTo("Updated Title");
        assertThat(result.getRent()).isEqualTo(35000.0);
    }

    @Test
    @DisplayName("updateProperty - admin can update any property")
    void updateProperty_byAdmin() {
        when(userRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(admin));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        PropertyRequest request = PropertyRequest.builder()
                .title("Admin Updated").description("Admin changed this")
                .address("999 Admin St").rent(40000)
                .type(PropertyType.APARTMENT).bedrooms(3).bathrooms(2)
                .furnished(true).size(100.0).amenities(List.of("Gym"))
                .build();

        PropertyResponse result = propertyService.updateProperty(10L, request, "admin@test.com");

        assertThat(result.getTitle()).isEqualTo("Admin Updated");
        assertThat(result.getRent()).isEqualTo(40000.0);
    }

    @Test
    @DisplayName("updateProperty - landlord cannot update other's property")
    void updateProperty_otherLandlordDenied() {
        User otherLandlord = User.builder().id(99L).email("other@test.com").role(Role.LANDLORD).build();
        when(userRepository.findByEmailIgnoreCase("other@test.com")).thenReturn(Optional.of(otherLandlord));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));

        PropertyRequest request = PropertyRequest.builder()
                .title("Hack").address("123").rent(1)
                .type(PropertyType.STUDIO).bedrooms(1).bathrooms(1).furnished(false).build();

        assertThatThrownBy(() -> propertyService.updateProperty(10L, request, "other@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cannot update properties they do NOT own");
    }

    @Test
    @DisplayName("updateProperty - tenant denied")
    void updateProperty_tenantDenied() {
        when(userRepository.findByEmailIgnoreCase("tenant@test.com")).thenReturn(Optional.of(tenant));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));

        PropertyRequest request = PropertyRequest.builder()
                .title("Hack").address("123").rent(1)
                .type(PropertyType.STUDIO).bedrooms(1).bathrooms(1).furnished(false).build();

        assertThatThrownBy(() -> propertyService.updateProperty(10L, request, "tenant@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("TENANT cannot update");
    }

    // ── deleteProperty ──────────────────────────────────────────────────

    @Test
    @DisplayName("deleteProperty - by owner")
    void deleteProperty_byOwner() {
        when(userRepository.findByEmailIgnoreCase("landlord@test.com")).thenReturn(Optional.of(landlord));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));

        propertyService.deleteProperty(10L, "landlord@test.com");

        verify(propertyRepository).delete(property);
        verify(auditLogService).log(any(), any(), eq(10L), anyString());
    }

    @Test
    @DisplayName("deleteProperty - tenant denied")
    void deleteProperty_tenantDenied() {
        when(userRepository.findByEmailIgnoreCase("tenant@test.com")).thenReturn(Optional.of(tenant));
        when(propertyRepository.findById(10L)).thenReturn(Optional.of(property));

        assertThatThrownBy(() -> propertyService.deleteProperty(10L, "tenant@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("TENANT cannot delete");
    }
}
