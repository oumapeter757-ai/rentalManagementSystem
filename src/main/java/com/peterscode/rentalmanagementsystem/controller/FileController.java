package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Management", description = "APIs for file upload and management")
public class FileController {

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    @Operation(summary = "Upload a file")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "general") String type) {

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("File is empty"));
            }

            // Create directory if not exists
            Path uploadPath = Paths.get(uploadDir, type);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String uniqueFilename = UUID.randomUUID() + fileExtension;
            Path filePath = uploadPath.resolve(uniqueFilename);

            // Save file
            Files.copy(file.getInputStream(), filePath);

            // Prepare response
            Map<String, String> response = new HashMap<>();
            response.put("filename", uniqueFilename);
            response.put("originalFilename", originalFilename);
            response.put("fileType", file.getContentType());
            response.put("fileSize", String.valueOf(file.getSize()));
            response.put("downloadUrl", "/api/files/download/" + type + "/" + uniqueFilename);
            response.put("viewUrl", "/uploads/" + type + "/" + uniqueFilename);

            log.info("File uploaded successfully: {}", uniqueFilename);
            return ResponseEntity.ok(ApiResponse.success("File uploaded successfully", response));

        } catch (IOException e) {
            log.error("File upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("File upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/download/{type}/{filename:.+}")
    @Operation(summary = "Download a file")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String type,
            @PathVariable String filename) {

        try {
            Path filePath = Paths.get(uploadDir, type, filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            // Determine content type
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Check backend health (for frontend)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> healthInfo = new HashMap<>();
        healthInfo.put("status", "UP");
        healthInfo.put("service", "Rental Management System API");
        healthInfo.put("timestamp", System.currentTimeMillis());
        healthInfo.put("version", "1.0.0");

        return ResponseEntity.ok(ApiResponse.success("Backend is healthy", healthInfo));
    }

    @GetMapping("/config")
    @Operation(summary = "Get frontend configuration")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFrontendConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("apiBaseUrl", "/api");
        config.put("uploadMaxSize", 10 * 1024 * 1024); // 10MB
        config.put("allowedFileTypes", new String[]{
                "image/jpeg", "image/png", "image/gif",
                "application/pdf", "application/msword"
        });
        config.put("pagination", Map.of(
                "defaultPageSize", 20,
                "maxPageSize", 100
        ));

        return ResponseEntity.ok(ApiResponse.success("Frontend configuration", config));
    }
}