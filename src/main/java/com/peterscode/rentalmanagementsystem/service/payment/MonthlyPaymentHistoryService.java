package com.peterscode.rentalmanagementsystem.service.payment;

import com.peterscode.rentalmanagementsystem.dto.response.MonthlyPaymentHistoryResponse;
import com.peterscode.rentalmanagementsystem.exception.ResourceNotFoundException;
import com.peterscode.rentalmanagementsystem.model.lease.Lease;
import com.peterscode.rentalmanagementsystem.model.payment.MonthlyPaymentHistory;
import com.peterscode.rentalmanagementsystem.model.payment.Payment;
import com.peterscode.rentalmanagementsystem.model.payment.PaymentStatus;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.MonthlyPaymentHistoryRepository;
import com.peterscode.rentalmanagementsystem.repository.PaymentRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyPaymentHistoryService {

    private final MonthlyPaymentHistoryRepository monthlyPaymentHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    /**
     * Get or create monthly payment history for a tenant
     */
    @Transactional
    public MonthlyPaymentHistory getOrCreateMonthlyHistory(User tenant, Property property, Lease lease,
                                                           Integer month, Integer year, BigDecimal rentAmount) {
        return monthlyPaymentHistoryRepository.findByTenantIdAndMonthAndYear(tenant.getId(), month, year)
                .orElseGet(() -> {
                    MonthlyPaymentHistory history = MonthlyPaymentHistory.builder()
                            .tenant(tenant)
                            .property(property)
                            .lease(lease)
                            .month(month)
                            .year(year)
                            .totalDue(rentAmount)
                            .totalPaid(BigDecimal.ZERO)
                            .balance(rentAmount)
                            .status(PaymentStatus.PENDING)
                            .paymentDeadline(LocalDate.of(year, month, 1).plusMonths(1).withDayOfMonth(5))
                            .build();
                    return monthlyPaymentHistoryRepository.save(history);
                });
    }

    /**
     * Update monthly history when a payment is made
     */
    @Transactional
    public void updateMonthlyHistory(Payment payment) {
        if (payment.getLease() == null || payment.getProperty() == null) {
            log.warn("Payment {} has no lease or property, skipping monthly history update", payment.getId());
            return;
        }

        LocalDateTime paidAt = payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
        int month = paidAt.getMonthValue();
        int year = paidAt.getYear();

        MonthlyPaymentHistory history = getOrCreateMonthlyHistory(
                payment.getTenant(),
                payment.getProperty(),
                payment.getLease(),
                month,
                year,
                payment.getLease().getMonthlyRent()
        );

        // Add payment amount to total paid
        history.setTotalPaid(history.getTotalPaid().add(payment.getAmount()));
        history.calculateBalance();

        monthlyPaymentHistoryRepository.save(history);
        log.info("Updated monthly payment history for tenant {} - Month: {}/{}",
                payment.getTenant().getId(), month, year);
    }

    /**
     * Get current month history for a tenant
     */
    @Transactional(readOnly = true)
    public MonthlyPaymentHistoryResponse getCurrentMonthHistory(String tenantEmail) {
        User tenant = userRepository.findByEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        MonthlyPaymentHistory history = monthlyPaymentHistoryRepository
                .findByTenantIdAndMonthAndYear(tenant.getId(), currentMonth, currentYear)
                .orElse(null);

        return history != null ? toResponse(history) : null;
    }

    /**
     * Get all payment history for a tenant
     */
    @Transactional(readOnly = true)
    public List<MonthlyPaymentHistoryResponse> getTenantPaymentHistory(String tenantEmail) {
        User tenant = userRepository.findByEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        return monthlyPaymentHistoryRepository.findByTenantIdOrderByYearDescMonthDesc(tenant.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get payment history for a specific year
     */
    @Transactional(readOnly = true)
    public List<MonthlyPaymentHistoryResponse> getTenantPaymentHistoryByYear(String tenantEmail, Integer year) {
        User tenant = userRepository.findByEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        return monthlyPaymentHistoryRepository.findByTenantIdAndYearOrderByMonthDesc(tenant.getId(), year)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get total balance for a tenant
     */
    @Transactional(readOnly = true)
    public BigDecimal getTenantTotalBalance(String tenantEmail) {
        User tenant = userRepository.findByEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        BigDecimal balance = monthlyPaymentHistoryRepository.getTotalBalanceByTenant(tenant.getId());
        return balance != null ? balance : BigDecimal.ZERO;
    }

    /**
     * Get total paid by a tenant
     */
    @Transactional(readOnly = true)
    public BigDecimal getTenantTotalPaid(String tenantEmail) {
        User tenant = userRepository.findByEmail(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        BigDecimal totalPaid = monthlyPaymentHistoryRepository.getTotalPaidByTenant(tenant.getId());
        return totalPaid != null ? totalPaid : BigDecimal.ZERO;
    }

    /**
     * Refresh monthly payment histories (called by scheduler or manually)
     */
    @Transactional
    public void refreshExpiredHistories() {
        // This can be called monthly to archive old data or reset for new billing cycles
        log.info("Refreshing monthly payment histories...");
        // Implementation for monthly refresh logic
    }

    private MonthlyPaymentHistoryResponse toResponse(MonthlyPaymentHistory history) {
        return MonthlyPaymentHistoryResponse.builder()
                .id(history.getId())
                .tenantId(history.getTenant().getId())
                .tenantEmail(history.getTenant().getEmail())
                .propertyId(history.getProperty().getId())
                .propertyTitle(history.getProperty().getTitle())
                .month(history.getMonth())
                .year(history.getYear())
                .totalDue(history.getTotalDue())
                .totalPaid(history.getTotalPaid())
                .balance(history.getBalance())
                .status(history.getStatus().name())
                .paymentDeadline(history.getPaymentDeadline())
                .createdAt(history.getCreatedAt())
                .updatedAt(history.getUpdatedAt())
                .build();
    }
}

