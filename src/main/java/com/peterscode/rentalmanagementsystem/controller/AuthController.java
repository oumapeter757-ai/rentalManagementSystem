package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.request.LoginRequest;
import com.peterscode.rentalmanagementsystem.dto.request.RegisterRequest;
import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.dto.response.JwtResponse;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user authentication and registration")
public class AuthController {

    private final AuthService authService;  // Change this to use the interface

    @PostMapping("/register/admin")
    @Operation(summary = "Register first admin (only works when no admin exists)")
    public ResponseEntity<ApiResponse<JwtResponse>> registerAdmin(@Valid @RequestBody RegisterRequest request) {
        JwtResponse response = authService.registerFirstAdmin(request);
        return ResponseEntity.ok(ApiResponse.success("Admin registered successfully", response));
    }

    @PostMapping("/register/tenant")
    @Operation(summary = "Register a new tenant")
    public ResponseEntity<ApiResponse<JwtResponse>> registerTenant(@Valid @RequestBody RegisterRequest request) {
        JwtResponse response = authService.registerTenant(request);
        return ResponseEntity.ok(ApiResponse.success("Tenant registered successfully", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login user")
    public ResponseEntity<ApiResponse<JwtResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        JwtResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/admin/create-user")
    @Operation(summary = "Admin creates a new user (LANDLORD or ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<JwtResponse>> createUser(
            @Valid @RequestBody RegisterRequest request,
            @RequestParam Role role) {
        JwtResponse response = authService.createUserByAdmin(request, role);
        return ResponseEntity.ok(ApiResponse.success("User created successfully", response));
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify email with token")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        boolean verified = authService.verifyEmail(token);
        if (verified) {
            return ResponseEntity.ok(ApiResponse.success("Email verified successfully", null));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email verification failed"));
        }
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email")
    public ResponseEntity<ApiResponse<Void>> resendVerificationEmail(@RequestParam String email) {
        authService.resendVerificationEmail(email);
        return ResponseEntity.ok(ApiResponse.success("Verification email resent", null));
    }
}