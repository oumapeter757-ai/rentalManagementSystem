package com.peterscode.rentalmanagementsystem.repository;

import com.peterscode.rentalmanagementsystem.model.sms.ReminderType;
import com.peterscode.rentalmanagementsystem.model.sms.SmsReminder;
import com.peterscode.rentalmanagementsystem.model.sms.SmsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SmsReminderRepository extends JpaRepository<SmsReminder, Long> {

    List<SmsReminder> findByStatus(SmsStatus status);

    List<SmsReminder> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<SmsReminder> findByReminderType(ReminderType reminderType);

    @Query("SELECT sr FROM SmsReminder sr WHERE sr.status = :status " +
           "AND sr.createdAt >= :since ORDER BY sr.createdAt DESC")
    List<SmsReminder> findRecentByStatus(@Param("status") SmsStatus status,
                                         @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(sr) FROM SmsReminder sr WHERE sr.tenant.id = :tenantId " +
           "AND sr.reminderType = :type AND sr.createdAt >= :since")
    long countRecentRemindersByType(@Param("tenantId") Long tenantId,
                                    @Param("type") ReminderType type,
                                    @Param("since") LocalDateTime since);
}

