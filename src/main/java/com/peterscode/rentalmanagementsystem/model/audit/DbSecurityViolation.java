package com.peterscode.rentalmanagementsystem.model.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Records every blocked attempt to directly manipulate the database
 * outside of the application (e.g., via MySQL CLI, Workbench, phpMyAdmin).
 */
@Entity
@Table(name = "db_security_violations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DbSecurityViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "operation", nullable = false, length = 10)
    private String operation;

    @Column(name = "attempted_by", nullable = false, length = 100)
    private String attemptedBy;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;
}

