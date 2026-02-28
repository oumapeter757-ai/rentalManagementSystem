package com.peterscode.rentalmanagementsystem.service;

import com.peterscode.rentalmanagementsystem.dto.request.PaymentRequest;
import com.peterscode.rentalmanagementsystem.dto.response.PaymentResponse;
import com.peterscode.rentalmanagementsystem.dto.response.PaymentSummaryResponse;
import com.peterscode.rentalmanagementsystem.exception.BadRequestException;
import com.peterscode.rentalmanagementsystem.exception.ResourceNotFoundException;
import com.peterscode.rentalmanagementsystem.model.lease.Lease;
import com.peterscode.rentalmanagementsystem.model.payment.Payment;
import com.peterscode.rentalmanagementsystem.model.payment.PaymentMethod;
import com.peterscode.rentalmanagementsystem.model.payment.PaymentStatus;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.LeaseRepository;
import com.peterscode.rentalmanagementsystem.repository.PaymentRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import com.peterscode.rentalmanagementsystem.service.audit.AuditLogService;
import com.peterscode.rentalmanagementsystem.service.payment.MpesaService;
import com.peterscode.rentalmanagementsystem.service.payment.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl Tests")
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LeaseRepository leaseRepository;
    @Mock
    private MpesaService mpesaService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User tenant;
    private User admin;
    private User landlord;
    private Property property;
    private Lease lease;
    private Payment pendingPayment;
    private Payment successfulPayment;

    @BeforeEach
    void setUp() {
        tenant = User.builder().id(1L).email("tenant@test.com").username("tenant")
                .password("enc").firstName("John").lastName("Doe").role(Role.TENANT)
                .phoneNumber("254712345678").build();

        admin = User.builder().id(2L).email("admin@test.com").username("admin")
                .password("enc").firstName("Admin").lastName("User").role(Role.ADMIN).build();

        landlord = User.builder().id(3L).email("landlord@test.com").username("landlord")
                .password("enc").firstName("Jane").lastName("Smith").role(Role.LANDLORD).build();

        property = Property.builder().id(10L).title("Test Apt")
                .owner(landlord).available(true).build();

        lease = Lease.builder().id(50L).tenant(tenant).property(property)
                .monthlyRent(BigDecimal.valueOf(25000)).deposit(BigDecimal.valueOf(10000))
                .depositPaid(false)
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusYears(1)).build();

        pendingPayment = Payment.builder()
                .id(100L).tenant(tenant).phoneNumber("254712345678")
                .amount(BigDecimal.valueOf(25000)).method(PaymentMethod.MPESA)
                .status(PaymentStatus.PENDING).transactionCode("TX-TEST-001")
                .callbackReceived(false).createdAt(LocalDateTime.now()).build();

        successfulPayment = Payment.builder()
                .id(200L).tenant(tenant).phoneNumber("254712345678")
                .amount(BigDecimal.valueOf(30000)).method(PaymentMethod.CASH)
                .status(PaymentStatus.SUCCESSFUL).transactionCode("TX-TEST-002")
                .callbackReceived(true).paidAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now()).build();
    }

    // ── createPayment ───────────────────────────────────────────────────

    @Test
    @DisplayName("createPayment - MPESA success + audit log recorded")
    void createPayment_mpesaSuccess() {
        PaymentRequest request = new PaymentRequest();
        request.setTenantId(1L);
        request.setAmount(BigDecimal.valueOf(25000));
        request.setPaymentMethod("MPESA");
        request.setPhoneNumber("0712345678");
        request.setNotes("Monthly rent");

        when(userRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmail("tenant@test.com")).thenReturn(Optional.of(tenant));
        when(mpesaService.formatPhoneNumber("0712345678")).thenReturn("254712345678");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(100L);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        PaymentResponse result = paymentService.createPayment(request, "tenant@test.com");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getAmount()).isEqualTo(BigDecimal.valueOf(25000));
        verify(paymentRepository).save(any(Payment.class));
        verify(mpesaService).formatPhoneNumber("0712345678");
        verify(auditLogService).log(any(), any(), eq(100L), anyString());
    }

    @Test
    @DisplayName("createPayment - CASH immediately SUCCESSFUL")
    void createPayment_cashImmediatelySuccessful() {
        PaymentRequest request = new PaymentRequest();
        request.setTenantId(1L);
        request.setAmount(BigDecimal.valueOf(25000));
        request.setPaymentMethod("CASH");
        request.setNotes("Cash payment");

        when(userRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmail("tenant@test.com")).thenReturn(Optional.of(tenant));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(101L);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        PaymentResponse result = paymentService.createPayment(request, "tenant@test.com");

        assertThat(result.getStatus()).isEqualTo("SUCCESSFUL");
    }

    @Test
    @DisplayName("createPayment - non-self, non-admin denied")
    void createPayment_nonSelfNonAdminDenied() {
        PaymentRequest request = new PaymentRequest();
        request.setTenantId(1L);
        request.setAmount(BigDecimal.valueOf(25000));
        request.setPaymentMethod("CASH");

        User otherTenant = User.builder().id(99L).email("other@test.com").role(Role.TENANT).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmail("other@test.com")).thenReturn(Optional.of(otherTenant));

        assertThatThrownBy(() -> paymentService.createPayment(request, "other@test.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only create payments for yourself");
    }

    // ── getPaymentById ──────────────────────────────────────────────────

    @Test
    @DisplayName("getPaymentById - found")
    void getPaymentById_found() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment));

        PaymentResponse result = paymentService.getPaymentById(100L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getPaymentById - not found")
    void getPaymentById_notFound() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getAllPayments role-based ────────────────────────────────────────

    @Test
    @DisplayName("getAllPayments - admin sees all")
    void getAllPayments_adminSeesAll() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(paymentRepository.findAll()).thenReturn(List.of(pendingPayment, successfulPayment));

        List<PaymentResponse> result = paymentService.getAllPayments("admin@test.com");

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getAllPayments - tenant sees empty")
    void getAllPayments_tenantSeesEmpty() {
        when(userRepository.findByEmail("tenant@test.com")).thenReturn(Optional.of(tenant));

        List<PaymentResponse> result = paymentService.getAllPayments("tenant@test.com");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAllPayments - landlord sees only own property payments")
    void getAllPayments_landlordFiltered() {
        Lease ownedLease = Lease.builder().id(50L).property(property).build();
        Payment leasePayment = Payment.builder()
                .id(300L).tenant(tenant).amount(BigDecimal.valueOf(25000))
                .method(PaymentMethod.CASH).status(PaymentStatus.SUCCESSFUL)
                .transactionCode("TX-300").lease(ownedLease).callbackReceived(true)
                .createdAt(LocalDateTime.now()).paidAt(LocalDateTime.now()).build();

        Payment noLeasePayment = Payment.builder()
                .id(301L).tenant(tenant).amount(BigDecimal.valueOf(10000))
                .method(PaymentMethod.CASH).status(PaymentStatus.SUCCESSFUL)
                .transactionCode("TX-301").callbackReceived(true)
                .createdAt(LocalDateTime.now()).build();

        when(userRepository.findByEmail("landlord@test.com")).thenReturn(Optional.of(landlord));
        when(paymentRepository.findAll()).thenReturn(List.of(leasePayment, noLeasePayment));

        List<PaymentResponse> result = paymentService.getAllPayments("landlord@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(300L);
    }

    // ── updatePaymentStatus + validateStatusTransition ───────────────────

    @Test
    @DisplayName("updatePaymentStatus - PENDING → SUCCESSFUL valid")
    void updatePaymentStatus_pendingToSuccessful() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse result = paymentService.updatePaymentStatus(100L, "SUCCESSFUL", "Test notes");

        assertThat(result.getStatus()).isEqualTo("SUCCESSFUL");
    }

    @Test
    @DisplayName("updatePaymentStatus - PENDING → REFUNDED invalid")
    void updatePaymentStatus_pendingToRefundedInvalid() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> paymentService.updatePaymentStatus(100L, "REFUNDED", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    @DisplayName("updatePaymentStatus - SUCCESSFUL → REFUNDED valid")
    void updatePaymentStatus_successfulToRefunded() {
        when(paymentRepository.findById(200L)).thenReturn(Optional.of(successfulPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse result = paymentService.updatePaymentStatus(200L, "REFUNDED", "Customer refund");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
    }

    // ── markAsPaid ──────────────────────────────────────────────────────

    @Test
    @DisplayName("markAsPaid - admin success")
    void markAsPaid_adminSuccess() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment));
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse result = paymentService.markAsPaid(100L, "MANUAL-TX-123", "admin@test.com");

        assertThat(result.getStatus()).isEqualTo("SUCCESSFUL");
        verify(auditLogService).log(any(), any(), eq(100L), anyString());
    }

    @Test
    @DisplayName("markAsPaid - landlord can mark as paid")
    void markAsPaid_landlordSuccess() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment));
        when(userRepository.findByEmail("landlord@test.com")).thenReturn(Optional.of(landlord));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse result = paymentService.markAsPaid(100L, "MANUAL-TX-456", "landlord@test.com");

        assertThat(result.getStatus()).isEqualTo("SUCCESSFUL");
    }

    @Test
    @DisplayName("markAsPaid - tenant denied")
    void markAsPaid_tenantDenied() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment));
        when(userRepository.findByEmail("tenant@test.com")).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> paymentService.markAsPaid(100L, "TX", "tenant@test.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only admins and landlords");
    }

    @Test
    @DisplayName("markAsPaid - already successful denied")
    void markAsPaid_alreadyPaid() {
        when(paymentRepository.findById(200L)).thenReturn(Optional.of(successfulPayment));
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> paymentService.markAsPaid(200L, "TX", "admin@test.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only pending or processing");
    }

    // ── getPaymentSummary ───────────────────────────────────────────────

    @Test
    @DisplayName("getPaymentSummary - calculates correctly")
    void getPaymentSummary_calculatesCorrectly() {
        when(paymentRepository.findAll()).thenReturn(List.of(pendingPayment, successfulPayment));

        PaymentSummaryResponse summary = paymentService.getPaymentSummary();

        assertThat(summary.getTotalTransactions()).isEqualTo(2);
        assertThat(summary.getSuccessfulTransactions()).isEqualTo(1);
        assertThat(summary.getPendingTransactions()).isEqualTo(1);
        assertThat(summary.getTotalSuccessful()).isEqualTo(BigDecimal.valueOf(30000));
        assertThat(summary.getTotalAmount()).isEqualTo(BigDecimal.valueOf(55000));
    }

    // ── getTotalRevenue ─────────────────────────────────────────────────

    @Test
    @DisplayName("getTotalRevenue - sums only SUCCESSFUL")
    void getTotalRevenue_onlySuccessful() {
        when(paymentRepository.findAll()).thenReturn(List.of(pendingPayment, successfulPayment));

        BigDecimal revenue = paymentService.getTotalRevenue();

        assertThat(revenue).isEqualTo(BigDecimal.valueOf(30000));
    }

    // ── isPaymentSuccessful / isPaymentPending ──────────────────────────

    @Test
    @DisplayName("isPaymentSuccessful - true for SUCCESSFUL")
    void isPaymentSuccessful_true() {
        when(paymentRepository.findById(200L)).thenReturn(Optional.of(successfulPayment));
        assertThat(paymentService.isPaymentSuccessful(200L)).isTrue();
    }

    @Test
    @DisplayName("isPaymentSuccessful - false for PENDING")
    void isPaymentSuccessful_false() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment));
        assertThat(paymentService.isPaymentSuccessful(100L)).isFalse();
    }

    @Test
    @DisplayName("isPaymentPending - true for PENDING")
    void isPaymentPending_true() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment));
        assertThat(paymentService.isPaymentPending(100L)).isTrue();
    }

    // ── updatePayment ───────────────────────────────────────────────────

    @Test
    @DisplayName("updatePayment - only pending can be updated")
    void updatePayment_onlyPending() {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(BigDecimal.valueOf(30000));

        when(paymentRepository.findById(200L)).thenReturn(Optional.of(successfulPayment));

        assertThatThrownBy(() -> paymentService.updatePayment(200L, request, "tenant@test.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only pending payments");
    }

    @Test
    @DisplayName("updatePayment - success for own pending payment")
    void updatePayment_successOwnPayment() {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(BigDecimal.valueOf(30000));
        request.setNotes("Updated amount");

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment));
        when(userRepository.findByEmail("tenant@test.com")).thenReturn(Optional.of(tenant));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse result = paymentService.updatePayment(100L, request, "tenant@test.com");

        assertThat(result.getAmount()).isEqualTo(BigDecimal.valueOf(30000));
    }

    // ── getPaymentsByStatus ─────────────────────────────────────────────

    @Test
    @DisplayName("getPaymentsByStatus - valid status")
    void getPaymentsByStatus_valid() {
        when(paymentRepository.findByStatus(PaymentStatus.PENDING)).thenReturn(List.of(pendingPayment));

        List<PaymentResponse> result = paymentService.getPaymentsByStatus("PENDING");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getPaymentsByStatus - invalid status throws")
    void getPaymentsByStatus_invalid() {
        assertThatThrownBy(() -> paymentService.getPaymentsByStatus("INVALID"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid payment status");
    }

    // ── getPaymentsByTenant ─────────────────────────────────────────────

    @Test
    @DisplayName("getPaymentsByTenant - returns tenant payments")
    void getPaymentsByTenant_returnsList() {
        when(paymentRepository.findByTenantId(1L)).thenReturn(List.of(pendingPayment));

        List<PaymentResponse> result = paymentService.getPaymentsByTenant(1L);

        assertThat(result).hasSize(1);
        verify(paymentRepository).findByTenantId(1L);
    }

    // ── deletePayment ───────────────────────────────────────────────────

    @Test
    @DisplayName("deletePayment - admin can delete pending")
    void deletePayment_adminSuccess() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(pendingPayment));
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        paymentService.deletePayment(100L, "admin@test.com");

        verify(paymentRepository).delete(pendingPayment);
    }

    @Test
    @DisplayName("deletePayment - cannot delete non-pending")
    void deletePayment_nonPendingDenied() {
        when(paymentRepository.findById(200L)).thenReturn(Optional.of(successfulPayment));

        assertThatThrownBy(() -> paymentService.deletePayment(200L, "admin@test.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only pending payments can be deleted");
    }

    // ── getPaymentOptions uses leaseRepository ──────────────────────────

    @Test
    @DisplayName("getPaymentOptions - tenant gets deposit option when not paid")
    void getPaymentOptions_depositNotPaid() {
        when(leaseRepository.findById(50L)).thenReturn(Optional.of(lease));
        when(userRepository.findByEmail("tenant@test.com")).thenReturn(Optional.of(tenant));

        var result = paymentService.getPaymentOptions(50L, "tenant@test.com");

        assertThat(result).isNotEmpty();
        verify(leaseRepository).findById(50L);
    }

    @Test
    @DisplayName("getPaymentOptions - non-owner non-admin denied")
    void getPaymentOptions_accessDenied() {
        User otherTenant = User.builder().id(99L).email("other@test.com").role(Role.TENANT).build();
        when(leaseRepository.findById(50L)).thenReturn(Optional.of(lease));
        when(userRepository.findByEmail("other@test.com")).thenReturn(Optional.of(otherTenant));

        assertThatThrownBy(() -> paymentService.getPaymentOptions(50L, "other@test.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only view payment options for your own leases");
    }
}
