package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.request.LoginRequest;
import com.peterscode.rentalmanagementsystem.dto.request.RegisterRequest;
import com.peterscode.rentalmanagementsystem.dto.request.ResetPasswordRequest;
import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.dto.response.JwtResponse;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user authentication and registration")
public class AuthController {

    private final AuthService authService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

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

    @PostMapping("/logout")
    @Operation(summary = "Logout user")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        try {
            // Extract token from header for logging (optional)
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                log.info("User logged out with token: {}", token.substring(0, 10) + "...");
            }

            log.info("✅ User logged out successfully");
            return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));

        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.success("Logged out", null));
        }
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
    public String verifyEmail(@RequestParam String token, Model model) {
        try {
            boolean verified = authService.verifyEmail(token);

            model.addAttribute("success", verified);
            model.addAttribute("frontendUrl", frontendUrl);
            model.addAttribute("currentYear", LocalDateTime.now().getYear());

            if (!verified) {
                model.addAttribute("message", "The verification token is invalid or has expired.");
            }

            return "verification-result";

        } catch (Exception e) {
            model.addAttribute("success", false);
            model.addAttribute("message", e.getMessage());
            model.addAttribute("frontendUrl", frontendUrl);
            model.addAttribute("currentYear", LocalDateTime.now().getYear());
            return "verification-result";
        }
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email")
    public ResponseEntity<ApiResponse<Void>> resendVerificationEmail(@RequestParam String email) {
        authService.resendVerificationEmail(email);
        return ResponseEntity.ok(ApiResponse.success("Verification email resent", null));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestParam String email) {
        authService.initiatePasswordReset(email);
        return ResponseEntity.ok(ApiResponse.success("Password reset instructions sent to your email", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", null));
    }
}