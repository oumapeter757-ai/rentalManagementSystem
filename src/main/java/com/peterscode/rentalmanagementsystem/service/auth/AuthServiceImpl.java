package com.peterscode.rentalmanagementsystem.service.auth;

import com.peterscode.rentalmanagementsystem.dto.request.EmailRequest;
import com.peterscode.rentalmanagementsystem.dto.request.LoginRequest;
import com.peterscode.rentalmanagementsystem.dto.request.RegisterRequest;
import com.peterscode.rentalmanagementsystem.dto.request.ResetPasswordRequest;
import com.peterscode.rentalmanagementsystem.dto.response.JwtResponse;
import com.peterscode.rentalmanagementsystem.exception.*;
import com.peterscode.rentalmanagementsystem.model.logs.PasswordResetToken;
import com.peterscode.rentalmanagementsystem.model.logs.VerificationToken;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.PasswordResetTokenRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import com.peterscode.rentalmanagementsystem.repository.VerificationTokenRepository;
import com.peterscode.rentalmanagementsystem.security.JwtService;
import com.peterscode.rentalmanagementsystem.security.SecurityUser;
import com.peterscode.rentalmanagementsystem.service.email.EmailService;
import com.peterscode.rentalmanagementsystem.util.NetworkUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;



@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:5174}")
    private String frontendUrl;

    private static final int RESET_CODE_EXPIRY_MINUTES = 5;
    private static final int VERIFICATION_TOKEN_EXPIRY_HOURS = 24;

    // ─── SecureRandom is cryptographically stronger than Random ───────────────
    private static final SecureRandom secureRandom = new SecureRandom();

    private String generateResetCode() {
        int code = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(code);
    }

    // ─── Shared helper: create + save + send verification token ──────────────
    private void createAndSendVerificationToken(User user) {
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusHours(VERIFICATION_TOKEN_EXPIRY_HOURS))
                .build();
        verificationTokenRepository.save(verificationToken);
        sendVerificationEmail(user, token);
    }

    // ─── Shared helper: build JwtResponse ────────────────────────────────────
    private JwtResponse buildJwtResponse(User user, String token) {
        return JwtResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .role(user.getRole().name())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .build();
    }

    // ─── Shared helper: build JwtResponse without token (pre-verification) ───
    private JwtResponse buildPendingVerificationResponse(User user) {
        return JwtResponse.builder()
                .tokenType("Bearer")
                .role(user.getRole().name())
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC METHODS
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean register(RegisterRequest request) {
        log.info("General registration attempt: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with email " + request.getEmail() + " already exists");
        }

        try {
            User user = User.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .phoneNumber(request.getPhoneNumber())
                    .role(Role.TENANT)
                    .enabled(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            User savedUser = userRepository.save(user);
            createAndSendVerificationToken(savedUser);

            log.info("User registered successfully: {}", savedUser.getEmail());
            return true;

        } catch (Exception e) {
            log.error("Error during user registration: {}", e.getMessage());
            throw new RuntimeException("Registration failed: " + e.getMessage(), e);
        }
    }

    @Override
    public JwtResponse registerFirstAdmin(RegisterRequest request) {
        log.info("Registering first admin: {}", request.getEmail());

        if (userRepository.countByRole(Role.ADMIN) > 0) {
            throw new AdminAlreadyExistsException("An admin already exists.");
        }

        validateEmailNotExists(request.getEmail());

        User admin = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .username(request.getUsername().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(Optional.ofNullable(request.getFirstName()).orElse("").trim())
                .lastName(Optional.ofNullable(request.getLastName()).orElse("").trim())
                .phoneNumber(Optional.ofNullable(request.getPhoneNumber()).orElse("").trim())
                .role(Role.ADMIN)
                .enabled(false)
                .build();

        User savedAdmin = userRepository.save(admin);
        createAndSendVerificationToken(savedAdmin);

        log.info("Admin registered. Verification email sent to: {}", savedAdmin.getEmail());

        // No JWT yet — admin must verify email first
        return buildPendingVerificationResponse(savedAdmin);
    }

    @Override
    public JwtResponse createUserByAdmin(RegisterRequest request, Role role) {
        log.info("Admin creating {}: {}", role, request.getEmail());

        if (role == Role.TENANT) {
            throw new InvalidRequestException("Admins cannot create tenants directly.");
        }

        validateEmailNotExists(request.getEmail());

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .username(request.getUsername().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(Optional.ofNullable(request.getFirstName()).orElse("").trim())
                .lastName(Optional.ofNullable(request.getLastName()).orElse("").trim())
                .phoneNumber(Optional.ofNullable(request.getPhoneNumber()).orElse("").trim())
                .role(role)
                .enabled(false) // must verify email before login
                .build();

        User savedUser = userRepository.save(user);
        createAndSendVerificationToken(savedUser);
        sendRegistrationEmail(savedUser, role.name());

        log.info("{} created. Verification email sent to: {}", role, savedUser.getEmail());

        // No JWT yet — user must verify email first
        return buildPendingVerificationResponse(savedUser);
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
                .phoneNumber(Optional.ofNullable(request.getPhoneNumber()).orElse("").trim())
                .role(Role.TENANT)
                .enabled(false) // must verify email before login
                .build();

        User savedTenant = userRepository.save(tenant);
        createAndSendVerificationToken(savedTenant);

        log.info("Tenant registered. Verification email sent to: {}", savedTenant.getEmail());

        // No JWT yet — tenant must verify email first
        return buildPendingVerificationResponse(savedTenant);
    }

    @Override
    public JwtResponse login(LoginRequest request) {
        return login(request, null);
    }

    @Override
    public JwtResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        log.info("Login attempt: {}", request.getEmail());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase().trim(),
                            request.getPassword()));

            SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
            if (securityUser == null || securityUser.user() == null) {
                throw new AuthenticationFailedException("Authentication failed - invalid user");
            }

            User user = securityUser.user();

            if (!user.isEnabled()) {
                throw new AccountDisabledException("Account is not verified. Please verify your email first.");
            }

            String ipAddress = httpRequest != null ? NetworkUtil.getClientIp(httpRequest) : "Unknown";
            sendLoginEmail(user, ipAddress);

            String token = jwtService.generateToken(user);
            return buildJwtResponse(user, token);

        } catch (BadCredentialsException ex) {
            throw new AuthenticationFailedException("Invalid email or password");
        }
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

        verificationTokenRepository.delete(verificationToken);
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

        verificationTokenRepository.deleteByUser(user);
        createAndSendVerificationToken(user);

        log.info("Verification email resent to: {}", email);
    }

    @Override
    public boolean adminExists() {
        return userRepository.countByRole(Role.ADMIN) > 0;
    }

    @Override
    public void initiatePasswordReset(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        String resetCode = generateResetCode();
        LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(RESET_CODE_EXPIRY_MINUTES);

        passwordResetTokenRepository.deleteByUser(user);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(resetCode)
                .user(user)
                .expiryDate(expiryDate)
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);

        // ⚠️ Do NOT log the actual reset code — remove this line before going to prod
        // log.info("🔐 Password reset code for {}: {}", email, resetCode);

        sendPasswordResetCodeEmail(user, resetCode);
        log.info("Password reset code sent to: {}", email);
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Password reset attempt");

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired reset code"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset code has expired. Please request a new one.");
        }

        if (resetToken.isUsed()) {
            throw new BadRequestException("Reset code has already been used");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        sendPasswordChangedEmail(user);
        log.info("Password reset successful for user: {}", user.getEmail());
    }

    @Override
    public boolean validateResetCode(String code) {
        log.info("Validating reset code");

        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(code)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid reset code"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset code has expired");
        }

        if (resetToken.isUsed()) {
            throw new BadRequestException("Reset code has already been used");
        }

        return true;
    }

    @Override
    public JwtResponse getUserInfoByEmail(String email) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        return buildPendingVerificationResponse(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void validateEmailNotExists(String email) {
        if (userRepository.existsByEmailIgnoreCase(email.toLowerCase().trim())) {
            throw new UserAlreadyExistsException("Email already exists: " + email);
        }
    }

    private void sendVerificationEmail(User user, String token) {
        try {
            String verificationLink = frontendUrl + "/verify-email?token=" + token;

            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("verificationLink", verificationLink);
            variables.put("expiryHours", VERIFICATION_TOKEN_EXPIRY_HOURS);

            EmailRequest emailRequest = EmailRequest.builder()
                    .recipient(user.getEmail())
                    .subject("Verify Your Email - Rental Management System")
                    .templateName("email-verification")
                    .variables(variables)
                    .html(true)
                    .build();

            emailService.sendEmailAsync(emailRequest);
            log.info("Verification email sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendRegistrationEmail(User user, String userType) {
        try {
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("lastName", user.getLastName());
            variables.put("userType", userType);
            variables.put("email", user.getEmail());
            variables.put("registeredTime", currentTime);

            EmailRequest emailRequest = EmailRequest.builder()
                    .recipient(user.getEmail())
                    .subject("Welcome to Rental Management System - Registration Successful")
                    .templateName("registration-success")
                    .variables(variables)
                    .html(true)
                    .build();

            emailService.sendEmailAsync(emailRequest);
            log.info("Registration email sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send registration email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendWelcomeEmail(User user) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("frontendUrl", frontendUrl);

            EmailRequest emailRequest = EmailRequest.builder()
                    .recipient(user.getEmail())
                    .subject("Welcome to Rental Management System!")
                    .templateName("welcome")
                    .variables(variables)
                    .html(true)
                    .build();

            emailService.sendEmailAsync(emailRequest);
            log.info("Welcome email sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendLoginEmail(User user, String ipAddress) {
        try {
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("email", user.getEmail());
            variables.put("loginTime", currentTime);
            variables.put("ipAddress", ipAddress);

            EmailRequest emailRequest = EmailRequest.builder()
                    .recipient(user.getEmail())
                    .subject("Security Alert: Successful Login to Rental Management System")
                    .templateName("login-success")
                    .variables(variables)
                    .html(true)
                    .build();

            emailService.sendEmailAsync(emailRequest);
            log.info("Login notification email sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send login email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendPasswordResetCodeEmail(User user, String resetCode) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("resetCode", resetCode);
            variables.put("expiryMinutes", RESET_CODE_EXPIRY_MINUTES);
            variables.put("currentYear", LocalDateTime.now().getYear());

            EmailRequest emailRequest = EmailRequest.builder()
                    .recipient(user.getEmail())
                    .subject("Your Password Reset Code - Rental Management System")
                    .templateName("password-reset-code")
                    .variables(variables)
                    .html(true)
                    .build();

            emailService.sendEmailAsync(emailRequest);
            log.info("Password reset code email sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send password reset code email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendPasswordChangedEmail(User user) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("changedTime",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            EmailRequest emailRequest = EmailRequest.builder()
                    .recipient(user.getEmail())
                    .subject("Password Changed Successfully - Rental Management System")
                    .templateName("password-changed")
                    .variables(variables)
                    .html(true)
                    .build();

            emailService.sendEmailAsync(emailRequest);
            log.info("Password changed notification sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send password changed email to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}