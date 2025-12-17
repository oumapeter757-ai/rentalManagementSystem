package com.peterscode.rentalmanagementsystem.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@Slf4j
public class FileStorageUtil {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;


    public String storeMaintenanceImage(MultipartFile file, Long maintenanceRequestId, String uploadedBy) throws IOException {
        String subDirectory = "maintenance/" + maintenanceRequestId;
        return storeFile(file, subDirectory, uploadedBy);
    }


    public String storeFile(MultipartFile file, String subDirectory, String uploadedBy) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file");
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID() + fileExtension;

        // Create directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir, subDirectory);
        Files.createDirectories(uploadPath);

        // Save file
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);


        String fileUrl = String.format("%s/uploads/%s/%s",
                appUrl, subDirectory, uniqueFilename);

        log.info("File stored successfully: {}", fileUrl);
        return fileUrl;
    }


    public boolean deleteFile(String fileUrl) {
        try {
            // Extract path from URL
            String relativePath = fileUrl.replace(appUrl + "/uploads/", "");
            Path filePath = Paths.get(uploadDir, relativePath);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("File deleted successfully: {}", filePath);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Failed to delete file: {}", e.getMessage());
            return false;
        }
    }


    public String generateThumbnailUrl(String originalFileUrl, int width, int height) {

        return originalFileUrl;
    }

    public boolean isValidImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }


    public boolean isValidFileSize(MultipartFile file, long maxSizeInBytes) {
        return file.getSize() <= maxSizeInBytes;
    }


    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }


    public Path createMaintenanceDirectory(Long maintenanceRequestId) throws IOException {
        Path maintenancePath = Paths.get(uploadDir, "maintenance", maintenanceRequestId.toString());
        Files.createDirectories(maintenancePath);
        return maintenancePath;
    }


    public Path getMaintenanceDirectoryPath(Long maintenanceRequestId) {
        return Paths.get(uploadDir, "maintenance", maintenanceRequestId.toString());
    }


    public boolean maintenanceDirectoryExists(Long maintenanceRequestId) {
        Path dirPath = getMaintenanceDirectoryPath(maintenanceRequestId);
        return Files.exists(dirPath) && Files.isDirectory(dirPath);
    }


    public boolean deleteMaintenanceDirectory(Long maintenanceRequestId) {
        try {
            Path dirPath = getMaintenanceDirectoryPath(maintenanceRequestId);
            if (Files.exists(dirPath)) {
                Files.walk(dirPath)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.error("Failed to delete file: {}", path, e);
                            }
                        });
                log.info("Maintenance directory deleted: {}", dirPath);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Failed to delete maintenance directory: {}", e.getMessage());
            return false;
        }
    }
}