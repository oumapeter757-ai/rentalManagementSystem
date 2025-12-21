
package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management APIs")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all users (Admin only)")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user by ID (Admin only)")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));
    }

    @GetMapping("/role/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get users by role (Admin only)")
    public ResponseEntity<ApiResponse<List<User>>> getUsersByRole(@PathVariable Role role) {
        List<User> users = userService.getUsersByRole(role);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @GetMapping("/landlords")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Get all landlords (Admin only)")
    public ResponseEntity<ApiResponse<List<User>>> getLandlords() {
        List<User> landlords = userService.getUsersByRole(Role.LANDLORD);
        return ResponseEntity.ok(ApiResponse.success("Landlords retrieved successfully", landlords));
    }

    @GetMapping("/tenants")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all tenants (Admin only)")
    public ResponseEntity<ApiResponse<List<User>>> getTenants() {
        List<User> tenants = userService.getUsersByRole(Role.TENANT);
        return ResponseEntity.ok(ApiResponse.success("Tenants retrieved successfully", tenants));
    }

    @GetMapping("/admins")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all admins (Admin only)")
    public ResponseEntity<ApiResponse<List<User>>> getAdmins() {
        List<User> admins = userService.getUsersByRole(Role.ADMIN);
        return ResponseEntity.ok(ApiResponse.success("Admins retrieved successfully", admins));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user (Admin only)")
    public ResponseEntity<ApiResponse<User>> updateUser(
            @PathVariable Long id,
            @RequestBody User userDetails) {
        User updatedUser = userService.updateUser(id, userDetails);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", updatedUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete user (Admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Toggle user status (Admin only)")
    public ResponseEntity<ApiResponse<User>> toggleUserStatus(
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        User updatedUser = userService.toggleUserStatus(id, enabled);
        return ResponseEntity.ok(ApiResponse.success("User status updated successfully", updatedUser));
    }
}