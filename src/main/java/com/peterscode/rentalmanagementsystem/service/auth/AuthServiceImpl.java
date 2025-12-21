package com.peterscode.rentalmanagementsystem.service.auth;

import com.peterscode.rentalmanagementsystem.config.AppConfig;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Random;

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

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    private final AppConfig appConfig;

    @Value("${app.frontend-url:http://localhost:5174}")
    private String frontendUrl;

    // Add this constant for code expiration (5 minutes)
    private static final int RESET_CODE_EXPIRY_MINUTES = 5;

    // Add this method for generating 6-digit code
    private String generateResetCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000); // 6-digit code (100000-999999)
        return String.valueOf(code);
    }

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
                .phoneNumber(Optional.ofNullable(request.getPhoneNumber()).orElse("").trim())
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
                .phoneNumber(Optional.ofNullable(request.getPhoneNumber()).orElse("").trim())
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
                .phoneNumber(Optional.ofNullable(request.getPhoneNumber()).orElse("").trim())
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


        User tenant = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .username(request.getUsername().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(Optional.ofNullable(request.getFirstName()).orElse("").trim())
                .lastName(Optional.ofNullable(request.getLastName()).orElse("").trim())
                .phoneNumber(Optional.ofNullable(request.getPhoneNumber()).orElse("").trim())
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
            User user = securityUser.user();

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

    // Update the initiatePasswordReset method for 6-digit code
    @Override
    public void initiatePasswordReset(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        // Generate 6-digit code
        String resetCode = generateResetCode();
        LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(RESET_CODE_EXPIRY_MINUTES);

        // Invalidate any existing reset codes for this user
        passwordResetTokenRepository.deleteByUser(user);

        // Create password reset token with code
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(resetCode)  // Store the 6-digit code as token
                .user(user)
                .expiryDate(expiryDate)
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);

        // Log the code (for testing/debugging)
        log.info("🔐 Password reset code for {}: {} (Expires in {} minutes)",
                email, resetCode, RESET_CODE_EXPIRY_MINUTES);

        // Send email with the 6-digit code
        sendPasswordResetCodeEmail(user, resetCode);

        log.info("Password reset code sent to: {}", email);
    }

    // Update the resetPassword method to accept code
    @Override
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Password reset attempt with token: {}", request.getToken().substring(0, Math.min(request.getToken().length(), 10)) + "...");

        // Validate passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        // Find token by code
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired reset code"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset code has expired. Please request a new one.");
        }

        if (resetToken.isUsed()) {
            throw new BadRequestException("Reset code has already been used");
        }

        User user = resetToken.getUser();

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        // Send password changed notification
        sendPasswordChangedEmail(user);

        log.info("Password reset successful for user: {}", user.getEmail());
    }

    // Add this method for validating reset code (without resetting password)
    public boolean validateResetCode(String code) {
        log.info("Validating reset code: {}", code);

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

    // Helper methods
    private void validateEmailNotExists(String email) {
        if (userRepository.existsByEmailIgnoreCase(email.toLowerCase().trim())) {
            throw new UserAlreadyExistsException("Email already exists: " + email);
        }
    }

    private void sendVerificationEmail(User user, String token) {
        try {
            // Use frontend URL for verification link
            String verificationLink = frontendUrl + "/auth/verify-email?token=" + token;
            String subject = "Verify Your Email - Rental Management System";

            // Create variables map for template
            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("verificationLink", verificationLink);
            variables.put("expiryHours", 24);

            // Create EmailRequest with variables
            com.peterscode.rentalmanagementsystem.dto.request.EmailRequest emailRequest =
                    com.peterscode.rentalmanagementsystem.dto.request.EmailRequest.builder()
                            .recipient(user.getEmail())
                            .subject(subject)
                            .templateName("email-verification")
                            .variables(variables)
                            .html(true)
                            .build();

            emailService.sendEmailAsync(emailRequest);
            log.info("📧 Verification email sent to: {}", user.getEmail());

            // Also log the verification link for manual testing (development)
            log.info("🔗 Verification link for manual testing: {}", verificationLink);

        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendRegistrationEmail(User user, String userType) {
        try {
            String subject = "Welcome to Rental Management System - Registration Successful";
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // Create variables map
            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("lastName", user.getLastName());
            variables.put("userType", userType);
            variables.put("email", user.getEmail());
            variables.put("registeredTime", currentTime);

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
            String subject = "Welcome to Rental Management System!";

            // Create variables map
            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("frontendUrl", frontendUrl);

            com.peterscode.rentalmanagementsystem.dto.request.EmailRequest emailRequest =
                    com.peterscode.rentalmanagementsystem.dto.request.EmailRequest.builder()
                            .recipient(user.getEmail())
                            .subject(subject)
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
            String subject = "Security Alert: Successful Login to Rental Management System";
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // Create variables map
            Map<String, Object> variables = new HashMap<>();
            variables.put("email", user.getEmail());
            variables.put("loginTime", currentTime);
            variables.put("ipAddress", ipAddress);

            com.peterscode.rentalmanagementsystem.dto.request.EmailRequest emailRequest =
                    com.peterscode.rentalmanagementsystem.dto.request.EmailRequest.builder()
                            .recipient(user.getEmail())
                            .subject(subject)
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

    // Add this method for sending password reset code email
    private void sendPasswordResetCodeEmail(User user, String resetCode) {
        try {
            String subject = "Your Password Reset Code - Rental Management System";

            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("resetCode", resetCode);
            variables.put("expiryMinutes", RESET_CODE_EXPIRY_MINUTES);
            variables.put("currentYear", LocalDateTime.now().getYear());

            EmailRequest emailRequest = EmailRequest.builder()
                    .recipient(user.getEmail())
                    .subject(subject)
                    .templateName("password-reset-code")
                    .variables(variables)
                    .html(true)
                    .build();

            emailService.sendEmailAsync(emailRequest);
            log.info("Password reset code email sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send password reset code email to {}: {}",
                    user.getEmail(), e.getMessage());
        }
    }

    private void sendPasswordChangedEmail(User user) {
        try {
            String subject = "Password Changed Successfully - Rental Management System";

            Map<String, Object> variables = new HashMap<>();
            variables.put("firstName", user.getFirstName());
            variables.put("changedTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            EmailRequest emailRequest = EmailRequest.builder()
                    .recipient(user.getEmail())
                    .subject(subject)
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