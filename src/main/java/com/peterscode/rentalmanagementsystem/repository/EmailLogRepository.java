
package com.peterscode.rentalmanagementsystem.repository;

import com.peterscode.rentalmanagementsystem.model.logs.EmailLog;
import com.peterscode.rentalmanagementsystem.model.logs.EmailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    Page<EmailLog> findByRecipient(String recipient, Pageable pageable);

    Page<EmailLog> findByStatus(EmailStatus status, Pageable pageable);

    Page<EmailLog> findByTemplateName(String templateName, Pageable pageable);

    List<EmailLog> findByStatusAndLastAttemptAtBefore(EmailStatus status, LocalDateTime dateTime);

    @Query("SELECT e FROM EmailLog e WHERE e.recipient LIKE %:searchTerm% OR e.subject LIKE %:searchTerm%")
    Page<EmailLog> searchByRecipientOrSubject(@Param("searchTerm") String searchTerm, Pageable pageable);

    long countByStatus(EmailStatus status);

    @Query("SELECT COUNT(e) FROM EmailLog e WHERE e.sentAt BETWEEN :startDate AND :endDate")
    long countEmailsSentBetween(@Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);
}