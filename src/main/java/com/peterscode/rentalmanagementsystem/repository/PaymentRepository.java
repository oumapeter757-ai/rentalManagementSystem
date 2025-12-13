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

    // New required methods for PaymentServiceImpl
    List<Payment> findByMethodAndCallbackReceivedFalse(PaymentMethod method);

    List<Payment> findByStatusAndMethodAndCallbackReceivedFalse(PaymentStatus status, PaymentMethod method);

    List<Payment> findByCallbackReceivedFalse();

    @Query("SELECT p FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = :status")
    List<Payment> findByTenantIdAndStatus(@Param("tenantId") Long tenantId,
                                          @Param("status") PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate);

    @Query("SELECT p FROM Payment p WHERE p.callbackReceived = false AND p.method = 'MPESA'")
    List<Payment> findPendingMpesaCallbacks();

    @Query("SELECT p FROM Payment p WHERE p.callbackReceived = false AND p.method = :method")
    List<Payment> findPendingCallbacksByMethod(@Param("method") PaymentMethod method);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = 'SUCCESSFUL'")
    BigDecimal getTotalSuccessfulAmount();

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = 'SUCCESSFUL'")
    BigDecimal getTotalSuccessfulAmountByTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = 'SUCCESSFUL'")
    Long countSuccessfulPayments();

    @Query("SELECT p FROM Payment p WHERE p.phoneNumber = :phoneNumber ORDER BY p.createdAt DESC")
    List<Payment> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    @Query("SELECT p FROM Payment p WHERE p.tenant.id = :tenantId AND p.status = :status AND p.method = :method")
    List<Payment> findByTenantIdAndStatusAndMethod(@Param("tenantId") Long tenantId,
                                                   @Param("status") PaymentStatus status,
                                                   @Param("method") PaymentMethod method);

    boolean existsByTransactionCode(String transactionCode);

    @Query("SELECT p FROM Payment p WHERE p.paidAt BETWEEN :startDate AND :endDate")
    List<Payment> findPaymentsByPaidDateRange(@Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.createdAt >= :date")
    List<Payment> findByStatusAndCreatedAfter(@Param("status") PaymentStatus status,
                                              @Param("date") LocalDateTime date);

    @Query("SELECT p FROM Payment p WHERE p.tenant.id = :tenantId AND p.createdAt >= :date")
    List<Payment> findByTenantIdAndCreatedAfter(@Param("tenantId") Long tenantId,
                                                @Param("date") LocalDateTime date);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = :status")
    BigDecimal getTotalAmountByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status")
    Long countByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.status IN :statuses")
    List<Payment> findByStatusIn(@Param("statuses") List<PaymentStatus> statuses);

    @Query("SELECT p FROM Payment p WHERE p.tenant.id = :tenantId AND p.status IN :statuses")
    List<Payment> findByTenantIdAndStatusIn(@Param("tenantId") Long tenantId,
                                            @Param("statuses") List<PaymentStatus> statuses);

    @Query("SELECT p FROM Payment p WHERE p.method = :method AND p.createdAt >= :date")
    List<Payment> findByMethodAndCreatedAfter(@Param("method") PaymentMethod method,
                                              @Param("date") LocalDateTime date);

    @Query("SELECT p FROM Payment p WHERE p.tenant.id = :tenantId ORDER BY p.createdAt DESC LIMIT :limit")
    List<Payment> findRecentPaymentsByTenant(@Param("tenantId") Long tenantId,
                                             @Param("limit") int limit);

    @Query("SELECT p FROM Payment p WHERE p.method = :method AND p.status = :status")
    List<Payment> findByMethodAndStatus(@Param("method") PaymentMethod method,
                                        @Param("status") PaymentStatus status);

    @Query("SELECT DISTINCT p.tenant.id FROM Payment p WHERE p.status = 'SUCCESSFUL'")
    List<Long> findTenantIdsWithSuccessfulPayments();

    @Query("SELECT p FROM Payment p WHERE p.notes LIKE %:keyword%")
    List<Payment> findByNotesContaining(@Param("keyword") String keyword);

    @Query("SELECT p FROM Payment p WHERE p.transactionCode LIKE %:partialCode%")
    List<Payment> findByTransactionCodeContaining(@Param("partialCode") String partialCode);

    @Query("SELECT EXTRACT(MONTH FROM p.paidAt) as month, SUM(p.amount) as total " +
            "FROM Payment p WHERE p.status = 'SUCCESSFUL' AND p.paidAt >= :startDate " +
            "GROUP BY EXTRACT(MONTH FROM p.paidAt) ORDER BY month")
    List<Object[]> getMonthlyRevenue(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.callbackReceived = false")
    Long countPendingCallbacks();
}