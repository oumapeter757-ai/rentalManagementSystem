package com.peterscode.rentalmanagementsystem.model.maintenance;

import com.peterscode.rentalmanagementsystem.model.maintenance.MaintenanceImage;
import com.peterscode.rentalmanagementsystem.model.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "maintenance_requests")
public class MaintenanceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;

    @Enumerated(EnumType.STRING)
    private MaintenanceCategory category;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private MaintenancePriority priority;

    @Enumerated(EnumType.STRING)
    private MaintenanceStatus status;

    private String notes;

    private LocalDateTime requestDate;

    @OneToMany(mappedBy = "maintenanceRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaintenanceImage> images = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        this.requestDate = LocalDateTime.now();
        if (this.status == null) {
            this.status = MaintenanceStatus.PENDING;
        }
    }
}

