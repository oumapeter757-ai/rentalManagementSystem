package com.peterscode.rentalmanagementsystem.service;

import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import com.peterscode.rentalmanagementsystem.service.user.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl Tests")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User admin;
    private User tenant;
    private User landlord;

    @BeforeEach
    void setUp() {
        admin = User.builder().id(1L).email("admin@test.com").username("admin")
                .password("encoded").firstName("Admin").lastName("User").role(Role.ADMIN).build();

        tenant = User.builder().id(2L).email("tenant@test.com").username("tenant")
                .password("encoded").firstName("John").lastName("Doe").role(Role.TENANT)
                .phoneNumber("0712345678").build();

        landlord = User.builder().id(3L).email("landlord@test.com").username("landlord")
                .password("encoded").firstName("Jane").lastName("Smith").role(Role.LANDLORD).build();
    }

    @Test
    @DisplayName("getAllUsers - returns all users")
    void getAllUsers_returnsList() {
        when(userRepository.findAll()).thenReturn(List.of(admin, tenant, landlord));

        List<User> result = userService.getAllUsers();

        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(admin, tenant, landlord);
    }

    @Test
    @DisplayName("getUserById - found")
    void getUserById_found() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(tenant));

        User result = userService.getUserById(2L);

        assertThat(result).isEqualTo(tenant);
        assertThat(result.getEmail()).isEqualTo("tenant@test.com");
    }

    @Test
    @DisplayName("getUserById - not found throws RuntimeException")
    void getUserById_notFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("getUsersByRole - returns filtered list")
    void getUsersByRole_returnsList() {
        when(userRepository.findByRole(Role.TENANT)).thenReturn(List.of(tenant));

        List<User> result = userService.getUsersByRole(Role.TENANT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo(Role.TENANT);
    }

    @Test
    @DisplayName("updateUser - partial fields updated")
    void updateUser_partialFields() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(tenant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updates = User.builder().firstName("Updated").phoneNumber("0799999999").build();

        User result = userService.updateUser(2L, updates);

        assertThat(result.getFirstName()).isEqualTo("Updated");
        assertThat(result.getPhoneNumber()).isEqualTo("0799999999");
        // Unchanged fields stay the same
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getEmail()).isEqualTo("tenant@test.com");
        verify(userRepository).save(tenant);
    }

    @Test
    @DisplayName("updateUser - null fields are not overwritten")
    void updateUser_nullFieldsNotOverwritten() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(tenant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updates = User.builder().firstName("NewName").build(); // only firstName set

        User result = userService.updateUser(2L, updates);

        assertThat(result.getFirstName()).isEqualTo("NewName");
        assertThat(result.getLastName()).isEqualTo("Doe"); // not overwritten
        assertThat(result.getPhoneNumber()).isEqualTo("0712345678"); // not overwritten
    }

    @Test
    @DisplayName("deleteUser - success")
    void deleteUser_success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(tenant));

        userService.deleteUser(2L);

        verify(userRepository).delete(tenant);
    }

    @Test
    @DisplayName("toggleUserStatus - enable")
    void toggleUserStatus_enable() {
        tenant.setEnabled(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(tenant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.toggleUserStatus(2L, true);

        assertThat(result.isEnabled()).isTrue();
        verify(userRepository).save(tenant);
    }

    @Test
    @DisplayName("toggleUserStatus - disable")
    void toggleUserStatus_disable() {
        tenant.setEnabled(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(tenant));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.toggleUserStatus(2L, false);

        assertThat(result.isEnabled()).isFalse();
    }
}
