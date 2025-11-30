package com.peterscode.rentalmanagementsystem.model.logs;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String recipientEmail;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 1000)
    private String body;

    @Column(nullable = false)
    private boolean sent;

    @Column(length = 500)
    private String errorMessage; // stores error if sending fails

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
