package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.request.LoginRequest;
import com.peterscode.rentalmanagementsystem.dto.request.RegisterRequest;
import com.peterscode.rentalmanagementsystem.dto.response.JwtResponse;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.service.auth.AuthServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthServiceImpl authService;

    @PostMapping("/register/admin")
    public ResponseEntity<JwtResponse> registerAdmin(@RequestBody RegisterRequest request) {
        JwtResponse response = authService.registerFirstAdmin(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register/tenant")
    public ResponseEntity<JwtResponse> registerTenant(@RequestBody RegisterRequest request) {
        JwtResponse response = authService.registerTenant(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest request) {
        JwtResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // Admin creates LANDLORD or ADMIN
    @PostMapping("/admin/create-user")
    public ResponseEntity<JwtResponse> createUser(@RequestBody RegisterRequest request,
                                                  @RequestParam Role role) {
        JwtResponse response = authService.createUserByAdmin(request, role);
        return ResponseEntity.ok(response);
    }
}
