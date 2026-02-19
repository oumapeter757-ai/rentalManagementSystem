package com.peterscode.rentalmanagementsystem.repository;

import com.peterscode.rentalmanagementsystem.model.payment.MonthlyPaymentHistory;
import com.peterscode.rentalmanagementsystem.model.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyPaymentHistoryRepository extends JpaRepository<MonthlyPaymentHistory, Long> {

    Optional<MonthlyPaymentHistory> findByTenantIdAndMonthAndYear(Long tenantId, Integer month, Integer year);

    List<MonthlyPaymentHistory> findByTenantIdOrderByYearDescMonthDesc(Long tenantId);

    List<MonthlyPaymentHistory> findByTenantIdAndYearOrderByMonthDesc(Long tenantId, Integer year);

    List<MonthlyPaymentHistory> findByTenantIdAndStatus(Long tenantId, PaymentStatus status);

    @Query("SELECT mph FROM MonthlyPaymentHistory mph WHERE mph.tenant.id = :tenantId " +
           "AND mph.year = :year AND mph.month = :month")
    Optional<MonthlyPaymentHistory> findCurrentMonthHistory(@Param("tenantId") Long tenantId,
                                                             @Param("year") Integer year,
                                                             @Param("month") Integer month);

    @Query("SELECT mph FROM MonthlyPaymentHistory mph WHERE mph.status = :status " +
           "AND mph.balance > 0 ORDER BY mph.paymentDeadline ASC")
    List<MonthlyPaymentHistory> findPendingPaymentsWithBalance(@Param("status") PaymentStatus status);

    @Query("SELECT SUM(mph.totalPaid) FROM MonthlyPaymentHistory mph WHERE mph.tenant.id = :tenantId")
    BigDecimal getTotalPaidByTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT SUM(mph.balance) FROM MonthlyPaymentHistory mph WHERE mph.tenant.id = :tenantId " +
           "AND mph.status = 'PENDING'")
    BigDecimal getTotalBalanceByTenant(@Param("tenantId") Long tenantId);

    boolean existsByTenantIdAndMonthAndYear(Long tenantId, Integer month, Integer year);

    List<MonthlyPaymentHistory> findByPropertyId(Long propertyId);
}

