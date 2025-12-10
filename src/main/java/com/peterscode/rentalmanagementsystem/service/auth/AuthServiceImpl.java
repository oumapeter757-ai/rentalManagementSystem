package com.peterscode.rentalmanagementsystem.service.auth;

import com.peterscode.rentalmanagementsystem.config.AppConfig;
import com.peterscode.rentalmanagementsystem.dto.request.LoginRequest;
import com.peterscode.rentalmanagementsystem.dto.request.RegisterRequest;
import com.peterscode.rentalmanagementsystem.dto.response.JwtResponse;
import com.peterscode.rentalmanagementsystem.exception.*;

import com.peterscode.rentalmanagementsystem.model.logs.VerificationToken;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;

import com.peterscode.rentalmanagementsystem.repository.UserRepository;

import com.peterscode.rentalmanagementsystem.repository.VerificationTokenRepository;
import com.peterscode.rentalmanagementsystem.security.JwtService;
import com.peterscode.rentalmanagementsystem.security.SecurityUser;

import com.peterscode.rentalmanagementsystem.service.email.EmailService;
import com.peterscode.rentalmanagementsystem.util.NetworkUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    private final AppConfig appConfig;


    @Override
    public boolean register(RegisterRequest request) {
        log.info("General registration attempt: {}", request.getEmail());

        // Validate email doesn't exist
        validateEmailNotExists(request.getEmail());

        // For general registration, default to TENANT role
        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .username(request.getUsername().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(Optional.ofNullable(request.getFirstName()).orElse("").trim())
                .lastName(Optional.ofNullable(request.getLastName()).orElse("").trim())
                .role(Role.TENANT)
                .enabled(false) // Require email verification
                .build();

        User savedUser = userRepository.save(user);

        // Create verification token
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .token(token)
                .user(savedUser)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .build();
        verificationTokenRepository.save(verificationToken);

        // Send verification email
        sendVerificationEmail(savedUser, token);


        log.info("User registered successfully: {}", savedUser.getEmail());
        return true;
    }

    @Override
    public JwtResponse login(LoginRequest request) {
        return login(request, null);
    }

    @Override
    public boolean verifyEmail(String token) {
        log.info("Email verification attempt for token: {}", token);

        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid verification token"));

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Verification token has expired");
        }

        User user = verificationToken.getUser();
        user.setEnabled(true);
        userRepository.save(user);

        // Delete used token
        verificationTokenRepository.delete(verificationToken);

        // Send welcome email
        sendWelcomeEmail(user);

        log.info("Email verified successfully for user: {}", user.getEmail());
        return true;
    }

    @Override
    public void resendVerificationEmail(String email) {
        log.info("Resend verification email requested for: {}", email);

        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (user.isEnabled()) {
            throw new BadRequestException("Account is already verified and enabled");
        }

        // Delete old tokens
        verificationTokenRepository.deleteByUser(user);

        // Create new verification token
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .build();
        verificationTokenRepository.save(verificationToken);

        // Resend verification email
        sendVerificationEmail(user, token);

        log.info("Verification email resent to: {}", email);
    }

    @Override
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
                .enabled(true) // Admin doesn't need email verification
                .build();

        User savedAdmin = userRepository.save(admin);

        // Send registration success email
        sendRegistrationEmail(savedAdmin, "Admin");

        String token = jwtService.generateToken(savedAdmin);
        return JwtResponse.builder()
                .token(token)
                .role(savedAdmin.getRole().name())
                .build();
    }

    @Override
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
                .enabled(true) // Admin-created users are automatically enabled
                .build();

        User savedUser = userRepository.save(user);

        // Send registration success email
        sendRegistrationEmail(savedUser, role.name());

        String token = jwtService.generateToken(savedUser);
        return JwtResponse.builder()
                .token(token)
                .role(savedUser.getRole().name())
                .build();
    }

    @Override
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
                .enabled(false) // Require email verification
                .build();

        User savedTenant = userRepository.save(tenant);

        // Create verification token
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .token(token)
                .user(savedTenant)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .build();
        verificationTokenRepository.save(verificationToken);

        // Send verification email
        sendVerificationEmail(savedTenant, token);

        String jwtToken = jwtService.generateToken(savedTenant);
        return JwtResponse.builder()
                .token(jwtToken)
                .role(savedTenant.getRole().name())
                .build();
    }

    @Override
    public JwtResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        log.info("Login attempt: {}", request.getEmail());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase().trim(),
                            request.getPassword()
                    )
            );

            SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
            User user = securityUser.getUser();

            if (!user.isEnabled()) {
                throw new AccountDisabledException("Account is not verified. Please verify your email first.");
            }

            // Get IP address if available
            String ipAddress = httpRequest != null ? NetworkUtil.getClientIp(httpRequest) : "Unknown";

            // Send login success email
            sendLoginEmail(user, ipAddress);

            String token = jwtService.generateToken(user);
            return JwtResponse.builder()
                    .token(token)
                    .role(user.getRole().name())
                    .build();

        } catch (BadCredentialsException ex) {
            throw new AuthenticationFailedException("Invalid email or password");
        }
    }

    // Helper methods
    private void validateEmailNotExists(String email) {
        if (userRepository.existsByEmailIgnoreCase(email.toLowerCase().trim())) {
            throw new UserAlreadyExistsException("Email already exists: " + email);
        }
    }

    private void sendVerificationEmail(User user, String token) {
        try {
            String verificationLink = appConfig.getVerificationUrl(token);
            String subject = "Verify Your Email - Rental Management System";

            String body = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .button { background-color: #4CAF50; color: white; padding: 12px 24px; 
                                 text-decoration: none; border-radius: 4px; display: inline-block; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h2>Email Verification</h2>
                        <p>Hello %s,</p>
                        <p>Please verify your email address by clicking the button below:</p>
                        <a href="%s" class="button">Verify Email</a>
                        <p>This link will expire in 24 hours.</p>
                        <p>If you didn't create an account, please ignore this email.</p>
                    </div>
                </body>
                </html>
                """.formatted(user.getFirstName(), verificationLink);

            com.peterscode.rentalmanagementsystem.dto.request.EmailRequest emailRequest =
                    com.peterscode.rentalmanagementsystem.dto.request.EmailRequest.builder()
                            .recipient(user.getEmail())
                            .subject(subject)
                            .body(body)
                            .templateName("email-verification")
                            .build();

            emailService.sendEmailAsync(emailRequest);
            log.info("Verification email sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendRegistrationEmail(User user, String userType) {
        try {
            String subject = "Welcome to Rental Management System - Registration Successful";
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String body = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h2>Registration Successful</h2>
                        <p>Hello %s %s,</p>
                        <p>Your account has been successfully registered as a <strong>%s</strong>.</p>
                        <p><strong>Email:</strong> %s</p>
                        <p><strong>Registered On:</strong> %s</p>
                        <p>You can now log in to the system.</p>
                    </div>
                </body>
                </html>
                """.formatted(user.getFirstName(), user.getLastName(), userType,
                    user.getEmail(), currentTime);

            com.peterscode.rentalmanagementsystem.dto.request.EmailRequest emailRequest =
                    com.peterscode.rentalmanagementsystem.dto.request.EmailRequest.builder()
                            .recipient(user.getEmail())
                            .subject(subject)
                            .body(body)
                            .templateName("registration-success")
                            .build();

            emailService.sendEmailAsync(emailRequest);
            log.info("Registration email sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send registration email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendWelcomeEmail(User user) {
        try {
            String subject = "Welcome to Rental Management System!";

            String body = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h2>Welcome Aboard!</h2>
                        <p>Hello %s,</p>
                        <p>Your email has been successfully verified. Your account is now fully activated.</p>
                        <p>You can now log in and start using all the features of our Rental Management System.</p>
                        <p>Thank you for joining us!</p>
                    </div>
                </body>
                </html>
                """.formatted(user.getFirstName());

            com.peterscode.rentalmanagementsystem.dto.request.EmailRequest emailRequest =
                    com.peterscode.rentalmanagementsystem.dto.request.EmailRequest.builder()
                            .recipient(user.getEmail())
                            .subject(subject)
                            .body(body)
                            .templateName("welcome")
                            .build();

            emailService.sendEmailAsync(emailRequest);
            log.info("Welcome email sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendLoginEmail(User user, String ipAddress) {
        try {
            String subject = "Security Alert: Successful Login to Rental Management System";
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String body = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .alert { background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="alert">
                            <h3>Security Alert</h3>
                            <p>A successful login was detected on your account.</p>
                        </div>
                        <p><strong>Account:</strong> %s</p>
                        <p><strong>Login Time:</strong> %s</p>
                        <p><strong>IP Address:</strong> %s</p>
                        <p>If this wasn't you, please contact support immediately.</p>
                    </div>
                </body>
                </html>
                """.formatted(user.getEmail(), currentTime, ipAddress);

            com.peterscode.rentalmanagementsystem.dto.request.EmailRequest emailRequest =
                    com.peterscode.rentalmanagementsystem.dto.request.EmailRequest.builder()
                            .recipient(user.getEmail())
                            .subject(subject)
                            .body(body)
                            .templateName("login-success")
                            .build();

            emailService.sendEmailAsync(emailRequest);
            log.info("Login notification email sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send login email to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}