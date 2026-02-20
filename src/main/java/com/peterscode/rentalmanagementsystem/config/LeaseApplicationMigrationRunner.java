package com.peterscode.rentalmanagementsystem.config;

import com.peterscode.rentalmanagementsystem.model.application.RentalApplication;
import com.peterscode.rentalmanagementsystem.model.application.RentalApplicationStatus;
import com.peterscode.rentalmanagementsystem.model.lease.Lease;
import com.peterscode.rentalmanagementsystem.repository.LeaseRepository;
import com.peterscode.rentalmanagementsystem.repository.RentalApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * On startup, ensures every existing lease has a corresponding RentalApplication record.
 * This fixes the "My Applications = 0" issue for tenants who paid before auto-creation was added.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(100) // Run after other migrations
public class LeaseApplicationMigrationRunner implements CommandLineRunner {

    private final LeaseRepository leaseRepository;
    private final RentalApplicationRepository rentalApplicationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            List<Lease> allLeases = leaseRepository.findAll();
            int created = 0;

            for (Lease lease : allLeases) {
                if (lease.getTenant() == null || lease.getProperty() == null) continue;

                Optional<RentalApplication> existingApp = rentalApplicationRepository
                        .findByPropertyIdAndTenantId(lease.getProperty().getId(), lease.getTenant().getId());

                if (existingApp.isEmpty()) {
                    RentalApplication app = RentalApplication.builder()
                            .tenant(lease.getTenant())
                            .property(lease.getProperty())
                            .status(RentalApplicationStatus.APPROVED)
                            .reason("Auto-created for existing lease #" + lease.getId())
                            .build();
                    rentalApplicationRepository.save(app);
                    created++;
                    log.info("Created application for tenant {} on property {} (lease #{})",
                            lease.getTenant().getId(), lease.getProperty().getId(), lease.getId());
                }
            }

            if (created > 0) {
                log.info("Lease-Application migration: created {} missing application records", created);
            } else {
                log.info("Lease-Application migration: all leases already have application records");
            }
        } catch (Exception e) {
            log.error("Lease-Application migration failed: {}", e.getMessage());
        }
    }
}

