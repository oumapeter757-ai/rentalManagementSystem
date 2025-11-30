package com.peterscode.rentalmanagementsystem.service.auth;

import com.peterscode.rentalmanagementsystem.dto.request.LoginRequest;
import com.peterscode.rentalmanagementsystem.dto.request.RegisterRequest;
import com.peterscode.rentalmanagementsystem.dto.response.JwtResponse;
import com.peterscode.rentalmanagementsystem.exception.*;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import com.peterscode.rentalmanagementsystem.security.JwtService;
import com.peterscode.rentalmanagementsystem.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // Admin registration with immediate token
    public JwtResponse registerFirstAdmin(RegisterRequest request) {
        log.info("Registering first admin: {}", request.getEmail());

        long adminCount = userRepository.countByRole(Role.ADMIN);
        if (adminCount > 0) {
            throw new AdminAlreadyExistsException("An admin already exists.");
        }

        validateEmailNotExists(request.getEmail());

        User admin = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .username(request.getUsername().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(Optional.ofNullable(request.getFirstName()).orElse("").trim())
                .lastName(Optional.ofNullable(request.getLastName()).orElse("").trim())
                .role(Role.ADMIN)
                .enabled(true)
                .build();

        User savedAdmin = userRepository.save(admin);

        String token = jwtService.generateToken(savedAdmin);
        return JwtResponse.builder()
                .token(token)
                .role(savedAdmin.getRole().name())
                .build();
    }

    // Admin creates other users (LANDLORD, ADMIN) with token
    public JwtResponse createUserByAdmin(RegisterRequest request, Role role) {
        log.info("Admin creating {}: {}", role, request.getEmail());

        validateEmailNotExists(request.getEmail());

        if (role == Role.TENANT) {
            throw new InvalidRequestException("Admins cannot create tenants directly.");
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .username(request.getUsername().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(Optional.ofNullable(request.getFirstName()).orElse("").trim())
                .lastName(Optional.ofNullable(request.getLastName()).orElse("").trim())
                .role(role)
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);

        String token = jwtService.generateToken(savedUser);
        return JwtResponse.builder()
                .token(token)
                .role(savedUser.getRole().name())
                .build();
    }

    // Tenant registration with immediate token
    public JwtResponse registerTenant(RegisterRequest request) {
        log.info("Registering tenant: {}", request.getEmail());

        validateEmailNotExists(request.getEmail());

        User tenant = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .username(request.getUsername().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(Optional.ofNullable(request.getFirstName()).orElse("").trim())
                .lastName(Optional.ofNullable(request.getLastName()).orElse("").trim())
                .role(Role.TENANT)
                .enabled(true)
                .build();

        User savedTenant = userRepository.save(tenant);

        String token = jwtService.generateToken(savedTenant);
        return JwtResponse.builder()
                .token(token)
                .role(savedTenant.getRole().name())
                .build();
    }

    public JwtResponse login(LoginRequest request) {
        log.info("Login attempt: {}", request.getEmail());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase().trim(),
                            request.getPassword()
                    )
            );

            SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
            User user = securityUser.getUser(); // <-- get the actual User object

            if (!user.isEnabled()) {
                throw new AccountDisabledException("Account is disabled.");
            }

            String token = jwtService.generateToken(user);
            return JwtResponse.builder()
                    .token(token)
                    .role(user.getRole().name())
                    .build();

        } catch (BadCredentialsException ex) {
            throw new AuthenticationFailedException("Invalid email or password");
        }
    }


    // Validate unique email
    private void validateEmailNotExists(String email) {
        if (userRepository.existsByEmailIgnoreCase(email.toLowerCase().trim())) {
            throw new UserAlreadyExistsException("Email already exists: " + email);
        }
    }
}
