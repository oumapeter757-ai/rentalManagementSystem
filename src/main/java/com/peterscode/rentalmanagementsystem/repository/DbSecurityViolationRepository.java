package com.peterscode.rentalmanagementsystem.repository;

import com.peterscode.rentalmanagementsystem.model.audit.DbSecurityViolation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface DbSecurityViolationRepository extends JpaRepository<DbSecurityViolation, Long> {

    Page<DbSecurityViolation> findAllByOrderByBlockedAtDesc(Pageable pageable);

    @Query("SELECT COUNT(v) FROM DbSecurityViolation v WHERE v.blockedAt >= :since")
    long countSince(LocalDateTime since);

    Page<DbSecurityViolation> findByTableNameIgnoreCaseOrderByBlockedAtDesc(String tableName, Pageable pageable);
}

