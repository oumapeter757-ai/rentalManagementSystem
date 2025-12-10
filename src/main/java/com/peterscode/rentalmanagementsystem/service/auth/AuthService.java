package com.peterscode.rentalmanagementsystem.service.auth;

import com.peterscode.rentalmanagementsystem.dto.request.LoginRequest;
import com.peterscode.rentalmanagementsystem.dto.request.RegisterRequest;
import com.peterscode.rentalmanagementsystem.dto.response.JwtResponse;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    // Existing methods (kept for compatibility)
    boolean register(RegisterRequest request);
    JwtResponse login(LoginRequest request);
    boolean verifyEmail(String token);
    void resendVerificationEmail(String email);

    // New methods for your implementation
    JwtResponse registerFirstAdmin(RegisterRequest request);
    JwtResponse createUserByAdmin(RegisterRequest request, Role role);
    JwtResponse registerTenant(RegisterRequest request);
    JwtResponse login(LoginRequest request, HttpServletRequest httpRequest);
}