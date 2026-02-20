package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.model.announcement.Announcement;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.AnnouncementRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
@Tag(name = "Announcements", description = "Announcement marquee APIs")
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;

    @GetMapping("/active")
    @Operation(summary = "Get all active announcements (for tenant marquee)")
    public ResponseEntity<ApiResponse<List<Announcement>>> getActiveAnnouncements() {
        List<Announcement> announcements = announcementRepository.findActiveAnnouncements(LocalDateTime.now());
        return ResponseEntity.ok(ApiResponse.success("Active announcements", announcements));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Get all announcements (admin/landlord)")
    public ResponseEntity<ApiResponse<List<Announcement>>> getAllAnnouncements() {
        List<Announcement> announcements = announcementRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(ApiResponse.success("All announcements", announcements));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Create a new announcement")
    public ResponseEntity<ApiResponse<Announcement>> createAnnouncement(@RequestBody Map<String, String> payload) {
        String message = payload.get("message");
        String expiresInHours = payload.get("expiresInHours");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Message is required"));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        User currentUser = userRepository.findByEmail(userDetails.getUsername()).orElse(null);

        LocalDateTime expiresAt = null;
        if (expiresInHours != null && !expiresInHours.isEmpty()) {
            try {
                expiresAt = LocalDateTime.now().plusHours(Long.parseLong(expiresInHours));
            } catch (NumberFormatException ignored) {}
        }

        Announcement announcement = Announcement.builder()
                .message(message.trim())
                .createdById(currentUser != null ? currentUser.getId() : 0L)
                .createdByName(currentUser != null ? currentUser.getFirstName() + " " + currentUser.getLastName() : "System")
                .createdByRole(currentUser != null ? currentUser.getRole().name() : "SYSTEM")
                .active(true)
                .expiresAt(expiresAt)
                .build();

        Announcement saved = announcementRepository.save(announcement);
        log.info("Announcement created by {}: {}", currentUser != null ? currentUser.getEmail() : "unknown", message);
        return ResponseEntity.ok(ApiResponse.success("Announcement created", saved));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Deactivate/stop an announcement")
    public ResponseEntity<ApiResponse<Void>> deactivateAnnouncement(@PathVariable Long id) {
        Announcement announcement = announcementRepository.findById(id).orElse(null);
        if (announcement == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Announcement not found"));
        }
        announcement.setActive(false);
        announcementRepository.save(announcement);
        log.info("Announcement {} deactivated", id);
        return ResponseEntity.ok(ApiResponse.success("Announcement stopped", null));
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANDLORD')")
    @Operation(summary = "Re-activate an announcement")
    public ResponseEntity<ApiResponse<Void>> activateAnnouncement(@PathVariable Long id) {
        Announcement announcement = announcementRepository.findById(id).orElse(null);
        if (announcement == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Announcement not found"));
        }
        announcement.setActive(true);
        announcementRepository.save(announcement);
        log.info("Announcement {} activated", id);
        return ResponseEntity.ok(ApiResponse.success("Announcement activated", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an announcement (admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteAnnouncement(@PathVariable Long id) {
        announcementRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Announcement deleted", null));
    }
}

