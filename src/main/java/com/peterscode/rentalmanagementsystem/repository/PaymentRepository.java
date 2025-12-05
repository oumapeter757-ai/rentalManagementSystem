package com.peterscode.rentalmanagementsystem.repository;

import com.peterscode.rentalmanagementsystem.model.payment.Payment;
import com.peterscode.rentalmanagementsystem.model.payment.PaymentMethod;
import com.peterscode.rentalmanagementsystem.model.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionCode(String transactionCode);

    List<Payment> findByTenantId(Long tenantId);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByMethod(PaymentMethod method);

    @Query("SELECT p FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = :status")
    List<Payment> findByTenantIdAndStatus(@Param("tenantId") Long tenantId,
                                          @Param("status") PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate);

    @Query("SELECT p FROM Payment p WHERE p.callbackReceived = false AND p.method = 'MPESA'")
    List<Payment> findPendingMpesaCallbacks();

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESSFUL'")
    BigDecimal getTotalSuccessfulAmount();

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = 'SUCCESSFUL'")
    BigDecimal getTotalSuccessfulAmountByTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = 'SUCCESSFUL'")
    Long countSuccessfulPayments();

    @Query("SELECT p FROM Payment p WHERE p.phoneNumber = :phoneNumber ORDER BY p.createdAt DESC")
    List<Payment> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    boolean existsByTransactionCode(String transactionCode);
}