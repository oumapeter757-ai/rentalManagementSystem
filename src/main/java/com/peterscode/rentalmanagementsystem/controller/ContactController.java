package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.model.contact.ContactMessage;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.ContactMessageRepository;
import com.peterscode.rentalmanagementsystem.repository.PropertyRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Contact", description = "Public contact & admin contact message endpoints")
public class ContactController {

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final ContactMessageRepository contactMessageRepository;
    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // ═══════════════════════════════════════
    //  PUBLIC ENDPOINTS  /api/public/*
    // ═══════════════════════════════════════

    @GetMapping("/api/public/contact-info")
    @Operation(summary = "Get admin contact info for public page (no auth)")
    public ResponseEntity<ApiResponse<Map<String, String>>> getContactInfo() {
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        Map<String, String> info = new LinkedHashMap<>();
        if (!admins.isEmpty()) {
            User admin = admins.get(0);
            info.put("email", admin.getEmail());
            info.put("phone", admin.getPhoneNumber() != null ? admin.getPhoneNumber() : "");
        }
        return ResponseEntity.ok(ApiResponse.success("Contact info retrieved", info));
    }

    @GetMapping("/api/public/stats")
    @Operation(summary = "Get public platform statistics (no auth)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPublicStats() {
        long totalProperties = propertyRepository.count();
        long totalTenants = userRepository.countByRole(Role.TENANT);
        long totalLandlords = userRepository.countByRole(Role.LANDLORD);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProperties", totalProperties);
        stats.put("totalTenants", totalTenants);
        stats.put("totalLandlords", totalLandlords);
        return ResponseEntity.ok(ApiResponse.success("Public stats retrieved", stats));
    }

    @PostMapping("/api/public/contact")
    @Operation(summary = "Submit a contact message (public, saved to DB + emailed to admin)")
    public ResponseEntity<ApiResponse<Object>> submitContact(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "").trim();

        if (message.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Message is required"));
        }

        // Save to DB
        ContactMessage cm = ContactMessage.builder().message(message).read(false).build();
        contactMessageRepository.save(cm);

        // Email all admins
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        for (User admin : admins) {
            if (admin.getEmail() != null && !admin.getEmail().isBlank()) {
                sendContactEmail(admin.getEmail(), message);
            }
        }

        log.info("📩 Contact message saved (id={}) and emailed to {} admin(s)", cm.getId(), admins.size());

        return ResponseEntity.ok(ApiResponse.success(
                "Thank you! Your message has been sent to our admin team.", null));
    }

    // ═══════════════════════════════════════
    //  ADMIN ENDPOINTS  /api/admin/contact-messages
    // ═══════════════════════════════════════

    @GetMapping("/api/admin/contact-messages")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all contact messages (Admin only)")
    public ResponseEntity<ApiResponse<List<ContactMessage>>> getAllContactMessages() {
        List<ContactMessage> messages = contactMessageRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(ApiResponse.success("Contact messages retrieved", messages));
    }

    @GetMapping("/api/admin/contact-messages/unread-count")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get unread contact message count (Admin only)")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        long count = contactMessageRepository.countByReadFalse();
        return ResponseEntity.ok(ApiResponse.success("Unread count", count));
    }

    @PutMapping("/api/admin/contact-messages/{id}/read")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mark a contact message as read (Admin only)")
    public ResponseEntity<ApiResponse<Object>> markAsRead(@PathVariable Long id) {
        ContactMessage cm = contactMessageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact message not found"));
        cm.setRead(true);
        contactMessageRepository.save(cm);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    @DeleteMapping("/api/admin/contact-messages/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a contact message (Admin only)")
    public ResponseEntity<ApiResponse<Object>> deleteMessage(@PathVariable Long id) {
        contactMessageRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Message deleted", null));
    }

    // ═══════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════

    @Async
    protected void sendContactEmail(String adminEmail, String message) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(adminEmail);
            helper.setSubject("📩 New Contact Message — RentalHub");
            helper.setText(
                "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>" +
                "  <div style='background:#6366f1;color:#fff;padding:20px;border-radius:12px 12px 0 0;text-align:center;'>" +
                "    <h2 style='margin:0;'>New Contact Message</h2>" +
                "  </div>" +
                "  <div style='background:#f8fafc;padding:24px;border:1px solid #e2e8f0;border-radius:0 0 12px 12px;'>" +
                "    <p style='color:#334155;font-size:15px;line-height:1.7;white-space:pre-wrap;'>" +
                        escapeHtml(message) +
                "    </p>" +
                "    <hr style='border:none;border-top:1px solid #e2e8f0;margin:20px 0;'/>" +
                "    <p style='color:#94a3b8;font-size:13px;'>Submitted via the RentalHub public contact form.</p>" +
                "  </div>" +
                "</div>", true);
            javaMailSender.send(mimeMessage);
            log.info("✅ Contact email sent to {}", adminEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send contact email to {}: {}", adminEmail, e.getMessage());
        }
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
