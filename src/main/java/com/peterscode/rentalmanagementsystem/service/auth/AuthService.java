package com.peterscode.rentalmanagementsystem.service.auth;

import com.peterscode.rentalmanagementsystem.dto.request.LoginRequest;
import com.peterscode.rentalmanagementsystem.dto.request.RegisterRequest;
import com.peterscode.rentalmanagementsystem.dto.response.JwtResponse;

public interface AuthService {

    boolean register(RegisterRequest request);

    JwtResponse login(LoginRequest request);

    boolean verifyEmail(String token);

    void resendVerificationEmail(String email);
}