package com.peterscode.rentalmanagementsystem.model.maintenance;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "maintenance_images")
public class MaintenanceImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String imageUrl;
    private String thumbnailUrl;
    private String caption;
    private String fileType;
    private Long fileSize;
    private String uploadedBy;

    @Column(name = "deleted")
    private Boolean deleted = false;

    private LocalDateTime uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_request_id")
    private MaintenanceRequest maintenanceRequest;

    @PrePersist
    public void preUpload() {
        this.uploadedAt = LocalDateTime.now();
    }
}
