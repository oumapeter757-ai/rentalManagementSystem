package com.peterscode.rentalmanagementsystem.model.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.peterscode.rentalmanagementsystem.config.EntityAuditListener;
import com.peterscode.rentalmanagementsystem.model.application.RentalApplication;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "users")
@EntityListeners(EntityAuditListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    @JsonIgnore  // SECURITY: Never expose password hashes in responses
    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "enabled")
    @Builder.Default
    private boolean enabled = true;


    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();



    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Property> properties;

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<RentalApplication> rentalApplications;







}
