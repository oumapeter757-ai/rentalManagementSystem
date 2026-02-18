package com.peterscode.rentalmanagementsystem.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.peterscode.rentalmanagementsystem.dto.request.MpesaStkRequest;
import com.peterscode.rentalmanagementsystem.dto.request.PaymentInitiationRequest;
import com.peterscode.rentalmanagementsystem.dto.request.PaymentRequest;
import com.peterscode.rentalmanagementsystem.dto.response.*;
import com.peterscode.rentalmanagementsystem.exception.BadRequestException;
import com.peterscode.rentalmanagementsystem.exception.ResourceNotFoundException;
import com.peterscode.rentalmanagementsystem.model.payment.Payment;
import com.peterscode.rentalmanagementsystem.model.payment.PaymentMethod;
import com.peterscode.rentalmanagementsystem.model.payment.PaymentStatus;
import com.peterscode.rentalmanagementsystem.model.payment.PaymentType;
import com.peterscode.rentalmanagementsystem.model.lease.Lease;
import com.peterscode.rentalmanagementsystem.model.lease.LeaseStatus;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.LeaseRepository;
import com.peterscode.rentalmanagementsystem.repository.PaymentRepository;
import com.peterscode.rentalmanagementsystem.repository.PropertyRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.peterscode.rentalmanagementsystem.model.payment.PaymentType.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final LeaseRepository leaseRepository;
    private final PropertyRepository propertyRepository;
    private final MpesaService mpesaService;
    private final StripeService stripeService; // Inject StripeService
    private final ObjectMapper objectMapper;



    @Override
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, String callerEmail) {
        log.info("Creating payment for tenant: {}", request.getTenantId());

        User tenant = userRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!caller.getId().equals(tenant.getId()) && caller.getRole() != Role.ADMIN) {
            throw new BadRequestException("You can only create payments for yourself");
        }

        String formattedPhone = null;
        if (request.getPhoneNumber() != null) {
            formattedPhone = mpesaService.formatPhoneNumber(request.getPhoneNumber());
        }

        Payment payment = Payment.builder()
                .tenant(tenant)
                .phoneNumber(formattedPhone)
                .amount(request.getAmount())
                .method(PaymentMethod.valueOf(request.getPaymentMethod().toUpperCase()))
                .status(PaymentStatus.PENDING)
                .transactionCode(generateTransactionCode())
                .notes(request.getNotes())
                .callbackReceived(false)
                .build();

        if (payment.getMethod() == PaymentMethod.CASH) {
            payment.setStatus(PaymentStatus.SUCCESSFUL);
            payment.setPaidAt(LocalDateTime.now());
        }

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment created with reference: {}", savedPayment.getTransactionCode());

        return PaymentResponse.fromEntity(savedPayment);
    }

    @Override
    public MpesaStkResponse initiateMpesaPayment(MpesaStkRequest request, String callerEmail) {
        log.info("Initiating M-Pesa STK Push for tenant: {}", request.getTenantId());

        User tenant = userRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!caller.getId().equals(tenant.getId()) && caller.getRole() != Role.ADMIN) {
            throw new BadRequestException("You can only initiate payments for yourself");
        }

        String phoneNumber = mpesaService.formatPhoneNumber(request.getPhoneNumber());

        try {
            String accountReference = "RENT-" + tenant.getId() + "-" + System.currentTimeMillis();
            String description = request.getDescription() != null ?
                    request.getDescription() : "Rent payment for " + tenant.getFirstName();

            MpesaStkResponse stkResponse = mpesaService.initiateStkPush(
                    phoneNumber, request.getAmount(), accountReference, description);

            if (stkResponse != null && stkResponse.isSuccessful()) {
                Payment payment = Payment.builder()
                        .tenant(tenant)
                        .phoneNumber(phoneNumber)
                        .amount(request.getAmount())
                        .method(PaymentMethod.MPESA)
                        .status(PaymentStatus.PROCESSING)
                        .transactionCode(stkResponse.getCheckoutRequestID())
                        .notes(request.getDescription())
                        .callbackReceived(false)
                        .gatewayResponse(stkResponse.getResponseDescription())
                        .build();

                paymentRepository.save(payment);
                log.info("M-Pesa STK Push initiated. CheckoutRequestID: {}", stkResponse.getCheckoutRequestID());

                // Schedule status check for 2 minutes later
                scheduleTransactionStatusCheck(stkResponse.getCheckoutRequestID());

                return stkResponse;
            } else {
                String errorMsg = stkResponse != null ?
                        stkResponse.getResponseDescription() : "No response from M-Pesa";
                throw new BadRequestException("M-Pesa STK Push failed: " + errorMsg);
            }
        } catch (Exception e) {
            log.error("Error initiating M-Pesa payment: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to initiate M-Pesa payment: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void processMpesaCallback(String callbackData) {
        log.info("Processing M-Pesa callback: {}", callbackData.substring(0, Math.min(callbackData.length(), 200)));

        try {
            Map<String, Object> callback = objectMapper.readValue(callbackData, Map.class);
            Map<String, Object> body = (Map<String, Object>) callback.get("Body");
            Map<String, Object> stkCallback = (Map<String, Object>) body.get("stkCallback");

            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");
            String resultCode = stkCallback.get("ResultCode").toString();
            String resultDesc = (String) stkCallback.get("ResultDesc");

            Optional<Payment> paymentOpt = paymentRepository.findByTransactionCode(checkoutRequestId);

            if (paymentOpt.isPresent()) {
                Payment payment = paymentOpt.get();

                switch (resultCode) {
                    case "0": // SUCCESS
                        handleSuccessfulCallback(payment, stkCallback);
                        break;
                    case "1": // Insufficient balance
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setNotes("Insufficient balance");
                        break;
                    case "1032": // User cancelled
                        payment.setStatus(PaymentStatus.CANCELLED);
                        payment.setNotes("Cancelled by user");
                        break;
                    case "17": // Didn't enter PIN
                        payment.setStatus(PaymentStatus.CANCELLED);
                        payment.setNotes("User didn't enter PIN");
                        break;
                    default: // Other failures
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setNotes("Failed: " + resultDesc);
                }

                payment.setCallbackReceived(true);
                payment.setGatewayResponse(callbackData);
                payment.setUpdatedAt(LocalDateTime.now());
                paymentRepository.save(payment);

                log.info("Payment {} updated to {} after callback", payment.getId(), payment.getStatus());
            } else {
                log.warn("No payment found for checkout request: {}", checkoutRequestId);
            }
        } catch (Exception e) {
            log.error("Error processing callback: {}", e.getMessage(), e);
        }

    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        return PaymentResponse.fromEntity(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByTransactionCode(String transactionCode) {
        Payment payment = paymentRepository.findByTransactionCode(transactionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with code: " + transactionCode));
        return PaymentResponse.fromEntity(payment);
    }

    @Transactional(readOnly = true)
    @Override
    public List<PaymentResponse> getAllPayments(String callerEmail) {
        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        // Admin sees all payments
        if (caller.getRole() == Role.ADMIN) {
            return paymentRepository.findAll().stream()
                    .map(PaymentResponse::fromEntity)
                    .collect(Collectors.toList());
        }
        
        // Landlord sees only payments for their properties (through leases)
        if (caller.getRole() == Role.LANDLORD) {
            return paymentRepository.findAll().stream()
                    .filter(payment -> {
                        if (payment.getLease() == null) return false;
                        Property property = payment.getLease().getProperty();
                        return property != null && 
                               property.getOwner() != null && 
                               property.getOwner().getId().equals(caller.getId());
                    })
                    .map(PaymentResponse::fromEntity)
                    .collect(Collectors.toList());
        }
        
        // Other roles (TENANT) see no payments via this endpoint (they should use /me)
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByTenant(Long tenantId) {
        return paymentRepository.findByTenantId(tenantId).stream()
                .map(PaymentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByStatus(String status) {
        try {
            PaymentStatus paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
            return paymentRepository.findByStatus(paymentStatus).stream()
                    .map(PaymentResponse::fromEntity)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid payment status: " + status);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByMethod(String method) {
        try {
            PaymentMethod paymentMethod = PaymentMethod.valueOf(method.toUpperCase());
            return paymentRepository.findByMethod(paymentMethod).stream()
                    .map(PaymentResponse::fromEntity)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid payment method: " + method);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyPayments(String callerEmail) {
        User user = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return getPaymentsByTenant(user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPendingMpesaCallbacks() {
        return paymentRepository.findByMethodAndCallbackReceivedFalse(PaymentMethod.MPESA).stream()
                .map(PaymentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PaymentResponse updatePaymentStatus(Long id, String status, String notes) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        try {
            PaymentStatus newStatus = PaymentStatus.valueOf(status.toUpperCase());
            PaymentStatus oldStatus = payment.getStatus();

            // Validate status transition
            validateStatusTransition(oldStatus, newStatus);

            // Update status
            payment.setStatus(newStatus);

            // Set paid timestamp if status changed to SUCCESSFUL
            if (newStatus == PaymentStatus.SUCCESSFUL && oldStatus != PaymentStatus.SUCCESSFUL) {
                payment.setPaidAt(LocalDateTime.now());
            }

            // Update notes if provided
            if (notes != null && !notes.trim().isEmpty()) {
                payment.setNotes(payment.getNotes() != null ?
                        payment.getNotes() + " | " + notes : notes);
            }

            payment.setUpdatedAt(LocalDateTime.now());
            Payment updatedPayment = paymentRepository.save(payment);

            log.info("Payment {} status updated from {} to {}",
                    payment.getTransactionCode(), oldStatus, newStatus);

            return PaymentResponse.fromEntity(updatedPayment);

        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid payment status: " + status);
        }
    }

    private void validateStatusTransition(PaymentStatus oldStatus, PaymentStatus newStatus) {
        // Define valid status transitions
        Map<PaymentStatus, List<PaymentStatus>> validTransitions = new HashMap<>();

        validTransitions.put(PaymentStatus.PENDING, Arrays.asList(
                PaymentStatus.PROCESSING, PaymentStatus.SUCCESSFUL, PaymentStatus.FAILED, PaymentStatus.CANCELLED
        ));

        validTransitions.put(PaymentStatus.PROCESSING, Arrays.asList(
                PaymentStatus.SUCCESSFUL, PaymentStatus.FAILED, PaymentStatus.CANCELLED
        ));

        validTransitions.put(PaymentStatus.SUCCESSFUL, Arrays.asList(
                PaymentStatus.REFUNDED, PaymentStatus.REVERSED, PaymentStatus.PARTIALLY_PAID
        ));

        validTransitions.put(PaymentStatus.FAILED, Arrays.asList(
                PaymentStatus.PENDING, PaymentStatus.CANCELLED
        ));

        List<PaymentStatus> allowedTransitions = validTransitions.get(oldStatus);
        if (allowedTransitions == null || !allowedTransitions.contains(newStatus)) {
            throw new BadRequestException(
                    String.format("Invalid status transition from %s to %s", oldStatus, newStatus)
            );
        }
    }

    @Override
    @Transactional
    public PaymentResponse updatePayment(Long id, PaymentRequest request, String callerEmail) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        // Only pending payments can be updated
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Only pending payments can be updated");
        }

        // Validate caller permissions
        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!caller.getId().equals(payment.getTenant().getId()) &&
                caller.getRole() != Role.ADMIN) {
            throw new BadRequestException("You can only update your own payments");
        }

        // Update fields
        if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            payment.setAmount(request.getAmount());
        }

        if (request.getPaymentMethod() != null) {
            payment.setMethod(PaymentMethod.valueOf(request.getPaymentMethod().toUpperCase()));
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()) {
            payment.setPhoneNumber(mpesaService.formatPhoneNumber(request.getPhoneNumber()));
        }

        if (request.getNotes() != null) {
            payment.setNotes(request.getNotes());
        }

        payment.setUpdatedAt(LocalDateTime.now());
        Payment updatedPayment = paymentRepository.save(payment);
        return PaymentResponse.fromEntity(updatedPayment);
    }

    @Override
    @Transactional
    public PaymentResponse markAsPaid(Long id, String transactionCode, String callerEmail) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        // Validate permissions
        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (caller.getRole() != Role.ADMIN &&
                caller.getRole() != Role.LANDLORD) {
            throw new BadRequestException("Only admins and landlords can manually mark payments as paid");
        }

        // Only allow marking pending payments as paid
        if (payment.getStatus() != PaymentStatus.PENDING &&
                payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new BadRequestException("Only pending or processing payments can be marked as paid");
        }

        // Update payment
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setPaidAt(LocalDateTime.now());

        if (transactionCode != null && !transactionCode.trim().isEmpty()) {
            payment.setTransactionCode(transactionCode);
        }

        payment.setUpdatedAt(LocalDateTime.now());
        Payment updatedPayment = paymentRepository.save(payment);

        log.info("Payment {} manually marked as paid by {}", id, callerEmail);

        return PaymentResponse.fromEntity(updatedPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSummaryResponse getPaymentSummary() {
        List<Payment> allPayments = paymentRepository.findAll();

        BigDecimal totalAmount = allPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSuccessful = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESSFUL)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPending = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING ||
                        p.getStatus() == PaymentStatus.PROCESSING)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalTransactions = allPayments.size();
        long successfulTransactions = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESSFUL)
                .count();
        long pendingTransactions = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING ||
                        p.getStatus() == PaymentStatus.PROCESSING)
                .count();
        long failedTransactions = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED)
                .count();
        long cancelledTransactions = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.CANCELLED)
                .count();
        long refundedTransactions = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.REFUNDED)
                .count();
        long reversedTransactions = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.REVERSED)
                .count();

        // Calculate by payment method
        Map<String, BigDecimal> amountByMethod = new HashMap<>();
        Map<String, Long> countByMethod = new HashMap<>();

        for (Payment payment : allPayments) {
            String method = payment.getMethod().name();
            amountByMethod.merge(method, payment.getAmount(), BigDecimal::add);
            countByMethod.merge(method, 1L, Long::sum);
        }

        // Calculate daily revenue for last 7 days
        Map<String, BigDecimal> dailyRevenue = new HashMap<>();
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESSFUL && p.getPaidAt() != null)
                .filter(p -> p.getPaidAt().isAfter(weekAgo))
                .forEach(p -> {
                    String date = p.getPaidAt().toLocalDate().toString();
                    dailyRevenue.merge(date, p.getAmount(), BigDecimal::add);
                });

        return PaymentSummaryResponse.builder()
                .totalAmount(totalAmount)
                .totalSuccessful(totalSuccessful)
                .totalPending(totalPending)
                .totalTransactions(totalTransactions)
                .successfulTransactions(successfulTransactions)
                .pendingTransactions(pendingTransactions)
                .failedTransactions(failedTransactions)
                .cancelledTransactions(cancelledTransactions)
                .refundedTransactions(refundedTransactions)
                .reversedTransactions(reversedTransactions)
                .amountByMethod(amountByMethod)
                .countByMethod(countByMethod)
                .dailyRevenue(dailyRevenue)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalRevenue() {
        List<Payment> allPayments = paymentRepository.findAll();
        return allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESSFUL)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalRevenueByTenant(Long tenantId) {
        List<Payment> tenantPayments = paymentRepository.findByTenantId(tenantId);
        return tenantPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESSFUL)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPaymentSuccessful(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        return payment.getStatus() == PaymentStatus.SUCCESSFUL;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPaymentPending(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        return payment.getStatus() == PaymentStatus.PENDING ||
                payment.getStatus() == PaymentStatus.PROCESSING;
    }

    @Override
    @Transactional
    public void deletePayment(Long id, String callerEmail) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        // Only pending payments can be deleted
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException("Only pending payments can be deleted");
        }

        // Validate permissions
        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!caller.getId().equals(payment.getTenant().getId()) &&
                caller.getRole() != Role.ADMIN) {
            throw new BadRequestException("You can only delete your own pending payments");
        }

        paymentRepository.delete(payment);
        log.info("Payment {} deleted by {}", id, callerEmail);
    }

    // ... [The rest of your existing methods: handleSuccessfulCallback, reversePayment,
    // refundPayment, queryMpesaTransactionStatus, checkPendingMpesaTransactions,
    // scheduleTransactionStatusCheck, generateTransactionCode remain the same]

    private void handleSuccessfulCallback(Payment payment, Map<String, Object> stkCallback) {
        try {
            Map<String, Object> callbackMetadata = (Map<String, Object>) stkCallback.get("CallbackMetadata");
            if (callbackMetadata != null) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) callbackMetadata.get("Item");

                String mpesaReceipt = null;
                String phoneNumber = null;
                BigDecimal amount = payment.getAmount();

                for (Map<String, Object> item : items) {
                    String name = (String) item.get("Name");
                    Object value = item.get("Value");

                    switch (name) {
                        case "MpesaReceiptNumber":
                            mpesaReceipt = value.toString();
                            break;
                        case "PhoneNumber":
                            phoneNumber = value.toString();
                            break;
                        case "Amount":
                            if (value instanceof Integer) {
                                amount = BigDecimal.valueOf((Integer) value);
                            } else if (value instanceof Double) {
                                amount = BigDecimal.valueOf((Double) value);
                            }
                            break;
                    }
                }

                payment.setStatus(PaymentStatus.SUCCESSFUL);
                payment.setTransactionCode(mpesaReceipt != null ? mpesaReceipt : payment.getTransactionCode());
                payment.setPhoneNumber(phoneNumber != null ? phoneNumber : payment.getPhoneNumber());
                payment.setAmount(amount);
                payment.setPaidAt(LocalDateTime.now());

                log.info("M-Pesa payment successful. Receipt: {}, Amount: {}", mpesaReceipt, amount);
                
                // ========== PROPERTY BOOKING LOGIC ==========
                // If deposit or full payment is successful, mark deposit as paid and book property
                if (payment.getPaymentType() == DEPOSIT ||
                    payment.getPaymentType() == FULL_AMOUNT) {
                    if (payment.getLease() != null) {
                        Lease lease = payment.getLease();
                        lease.setDepositPaid(true);
                        lease.setStatus(LeaseStatus.ACTIVE); // Activate the lease
                        leaseRepository.save(lease);
                        log.info("Marked deposit as paid and activated lease: {}", lease.getId());
                        
                        // Mark property as unavailable (booked)
                        Property property = lease.getProperty();
                        if (property != null) {
                            property.setAvailable(false);
                            propertyRepository.save(property);
                            log.info("Marked property {} as unavailable (booked)", property.getId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting callback metadata: {}", e.getMessage());
            payment.setStatus(PaymentStatus.SUCCESSFUL);
            payment.setPaidAt(LocalDateTime.now());
        }
    }

    @Override
    @Transactional
    public PaymentResponse reversePayment(Long id, String reversalReason, String callerEmail) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (caller.getRole() != Role.ADMIN) {
            throw new BadRequestException("Only admins can reverse payments");
        }

        if (payment.getStatus() != PaymentStatus.SUCCESSFUL) {
            throw new BadRequestException("Only successful payments can be reversed");
        }

        payment.setStatus(PaymentStatus.REVERSED);
        payment.setNotes("Reversed: " + reversalReason + " (by " + callerEmail + ")");
        payment.setUpdatedAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment {} reversed by {}", id, callerEmail);

        return PaymentResponse.fromEntity(savedPayment);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(Long id, BigDecimal refundAmount, String reason, String callerEmail) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (caller.getRole() != Role.ADMIN && caller.getRole() != Role.LANDLORD) {
            throw new BadRequestException("Only admins and landlords can process refunds");
        }

        if (payment.getStatus() != PaymentStatus.SUCCESSFUL) {
            throw new BadRequestException("Only successful payments can be refunded");
        }

        if (refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new BadRequestException("Refund amount cannot exceed original payment");
        }

        if (refundAmount.compareTo(payment.getAmount()) == 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setStatus(PaymentStatus.PARTIALLY_PAID);
            // Create a new payment record for the remaining balance
            Payment remainingPayment = Payment.builder()
                    .tenant(payment.getTenant())
                    .phoneNumber(payment.getPhoneNumber())
                    .amount(payment.getAmount().subtract(refundAmount))
                    .method(payment.getMethod())
                    .status(PaymentStatus.PENDING)
                    .transactionCode(generateTransactionCode())
                    .notes("Remaining balance after partial refund of " + refundAmount)
                    .callbackReceived(false)
                    .build();
            paymentRepository.save(remainingPayment);
        }

        payment.setNotes("Refunded: " + refundAmount + " - Reason: " + reason + " (by " + callerEmail + ")");
        payment.setUpdatedAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment {} refunded {} by {}", id, refundAmount, callerEmail);

        return PaymentResponse.fromEntity(savedPayment);
    }

    @Override
    public void queryMpesaTransactionStatus(String checkoutRequestId) {
        try {
            log.info("Querying M-Pesa transaction status for: {}", checkoutRequestId);
            MpesaTransactionStatusResponse statusResponse = mpesaService.queryTransactionStatus(checkoutRequestId);

            Optional<Payment> paymentOpt = paymentRepository.findByTransactionCode(checkoutRequestId);
            if (paymentOpt.isPresent() && !paymentOpt.get().isCallbackReceived()) {
                log.info("Transaction {} status: {}", checkoutRequestId, statusResponse.getResultDesc());

                // Update payment based on query result if needed
                Payment payment = paymentOpt.get();
                if (statusResponse.getResultCode() != null) {
                    if (statusResponse.getResultCode().equals("0")) {
                        payment.setStatus(PaymentStatus.SUCCESSFUL);
                        payment.setPaidAt(LocalDateTime.now());
                        payment.setCallbackReceived(true);
                        paymentRepository.save(payment);
                    } else if (statusResponse.getResultCode().equals("1032")) {
                        payment.setStatus(PaymentStatus.CANCELLED);
                        payment.setCallbackReceived(true);
                        paymentRepository.save(payment);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error querying transaction status: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentOptionResponse> getPaymentOptions(Long leaseId, String callerEmail) {
        log.info("Getting payment options for lease: {} by user: {}", leaseId, callerEmail);
        
        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease not found"));
        
        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        // Security: Verify tenant owns this lease or is admin
        if (!lease.getTenant().getId().equals(caller.getId()) && caller.getRole() != Role.ADMIN) {
            throw new BadRequestException("You can only view payment options for your own leases");
        }
        
        List<PaymentOptionResponse> options = new ArrayList<>();
        
        // Option 1: Deposit payment (if not paid)
        if (lease.getDepositPaid() == null || !lease.getDepositPaid()) {
            options.add(PaymentOptionResponse.builder()
                    .paymentType(DEPOSIT)
                    .amount(lease.getDeposit())
                    .description("Security Deposit")
                    .available(true)
                    .build());
        }
        
        // Option 2: Monthly rent (if deposit is paid or lease is active)
        if (lease.getDepositPaid() != null && lease.getDepositPaid()) {
            options.add(PaymentOptionResponse.builder()
                    .paymentType(RENT)
                    .amount(lease.getMonthlyRent())
                    .description("Monthly Rent")
                    .available(true)
                    .build());
        }
        
        // Option 3: Full payment (deposit + first month rent)
        if (lease.getDepositPaid() == null || !lease.getDepositPaid()) {
            BigDecimal fullAmount = lease.getDeposit().add(lease.getMonthlyRent());
            options.add(PaymentOptionResponse.builder()
                    .paymentType(FULL_PAYMENT)
                    .amount(fullAmount)
                    .description("Full Payment (Deposit + First Month Rent)")
                    .available(true)
                    .build());
        }
        
        return options;
    }

    @Override
    @Transactional
    public PaymentResponse initiatePayment(PaymentInitiationRequest request, String callerEmail) {
        log.info("Initiating payment initiation request by user: {}", callerEmail);
        
        User tenant = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Lease lease = null;

        // Case 1: Lease ID is provided
        if (request.getLeaseId() != null) {
            lease = leaseRepository.findById(request.getLeaseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lease not found"));
            
            // Security: Verify tenant owns this lease
            if (!lease.getTenant().getId().equals(tenant.getId()) && tenant.getRole() != Role.TENANT) {
                throw new BadRequestException("You can only create payments for your own leases");
            }
        } 
        // Case 2: Property ID is provided (Find existing or create new lease)
        else if (request.getPropertyId() != null) {
            Property property = propertyRepository.findById(request.getPropertyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

            // Check if tenant already has a lease for this property
            List<Lease> existingLeases = leaseRepository.findByPropertyAndTenant(property, tenant);
            // Filter for active or pending leases
            Optional<Lease> activeLease = existingLeases.stream()
                    .filter(l -> l.getStatus() == LeaseStatus.ACTIVE || l.getStatus() == LeaseStatus.PENDING)
                    .findFirst();

            if (activeLease.isPresent()) {
                lease = activeLease.get();
            } else {
                // Check if property is available
                if (!property.isAvailable() && !"AVAILABLE".equalsIgnoreCase(String.valueOf(property.getStatus()))) {
                     throw new BadRequestException("Property is not available for leasing");
                }

                // Create a new PENDING lease for this payment
                lease = Lease.builder()
                        .tenant(tenant)
                        .property(property)
                        .startDate(LocalDate.now())
                        .endDate(LocalDate.now().plusYears(1))
                        .monthlyRent(property.getRentAmount())
                        .deposit(property.getDepositAmount() != null ? property.getDepositAmount() : property.getRentAmount()) // Default deposit to rent if null
                        .depositPaid(false)
                        .status(LeaseStatus.PENDING)
                        .build();
                lease = leaseRepository.save(lease);
                log.info("Created new PENDING lease {} for tenant {} and property {}", lease.getId(), tenant.getId(), property.getId());
            }
        } else {
            throw new BadRequestException("Either Lease ID or Property ID must be provided");
        }
        
        // Calculate amount based on payment type - NO MANUAL ENTRY!
        BigDecimal amount;
        String paymentTypeStr = request.getPaymentType().toUpperCase();
        switch (paymentTypeStr) {
            case "DEPOSIT":
                if (lease.getDepositPaid() != null && lease.getDepositPaid()) {
                    throw new BadRequestException("Deposit has already been paid");
                }
                amount = lease.getDeposit();
                break;
                
            case "RENT":
                if (lease.getDepositPaid() == null || !lease.getDepositPaid()) {
                     // For flexible payments, we might allow this, but generally deposit comes first
                     // But if it's a new lease, they should pay deposit first or FULL_AMOUNT
                     // Let's enforce deposit first rule unless it's bundled
                    throw new BadRequestException("Deposit must be paid first before paying rent");
                }
                amount = lease.getMonthlyRent();
                break;
                
            case "FULL_AMOUNT":
            case "FULL_PAYMENT":
                if (lease.getDepositPaid() != null && lease.getDepositPaid()) {
                    throw new BadRequestException("Deposit has already been paid");
                }
                amount = lease.getDeposit().add(lease.getMonthlyRent());
                break;
                
            default:
                throw new BadRequestException("Invalid payment type");
        }
        
        // Format phone number for M-Pesa
        String formattedPhone = null;
        if ("MPESA".equalsIgnoreCase(request.getPaymentMethod())) {
            if (request.getPhoneNumber() == null || request.getPhoneNumber().isEmpty()) {
                throw new BadRequestException("Phone number is required for M-Pesa payments");
            }
            formattedPhone = mpesaService.formatPhoneNumber(request.getPhoneNumber());
        }
        
        // Create payment record
        Payment payment = Payment.builder()
                .tenant(tenant)
                .lease(lease)
                .phoneNumber(formattedPhone)
                .amount(amount)
                .method(PaymentMethod.valueOf(request.getPaymentMethod().toUpperCase()))
                .status(PaymentStatus.PENDING)
                .paymentType(PaymentType.valueOf(request.getPaymentType().toUpperCase()))
                .transactionCode(generateTransactionCode())
                .notes(request.getNotes())
                .callbackReceived(false)
                .gatewayResponse("Pending M-Pesa STK Push") // Set default to avoid NOT NULL constraint
                .build();
        
        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment created with transaction code: {}", savedPayment.getTransactionCode());
        
        // Initiate M-Pesa STK push if payment method is MPESA
        if ("MPESA".equalsIgnoreCase(request.getPaymentMethod())) {
            try {
                MpesaStkRequest stkRequest = MpesaStkRequest.builder()
                        .tenantId(tenant.getId())
                        .phoneNumber(formattedPhone)
                        .amount(amount)
                        .transactionCode(savedPayment.getTransactionCode())
                        .accountReference("LEASE-" + lease.getId())
                        .build();
                
                mpesaService.initiateStkPush(stkRequest);
                savedPayment.setStatus(PaymentStatus.PROCESSING);
                paymentRepository.save(savedPayment);
                log.info("M-Pesa STK push initiated successfully for payment: {}", savedPayment.getId());
                
            } catch (Exception e) {
                log.error("Failed to initiate M-Pesa STK push: {}", e.getMessage());
                savedPayment.setStatus(PaymentStatus.FAILED);
                savedPayment.setNotes("M-Pesa initiation failed: " + e.getMessage());
                paymentRepository.save(savedPayment);
            }
        } else if ("CARD".equalsIgnoreCase(request.getPaymentMethod()) || "CREDIT_CARD".equalsIgnoreCase(request.getPaymentMethod())) {
             try {
                 if (request.getPaymentToken() == null || request.getPaymentToken().isEmpty()) {
                     throw new BadRequestException("Payment token is required for card payments");
                 }

                 String description = "Rent Payment for " + lease.getProperty().getTitle();
                 String transactionId = stripeService.charge(
                         request.getPaymentToken(),
                         amount,
                         "kes", // Ensure currency matches your Stripe account settings (using KES as per project context)
                         description
                 );

                 savedPayment.setStatus(PaymentStatus.SUCCESSFUL);
                 savedPayment.setTransactionCode(transactionId);
                 savedPayment.setPaidAt(LocalDateTime.now());
                 savedPayment.setGatewayResponse("Stripe Charge ID: " + transactionId);
                 paymentRepository.save(savedPayment);
                 
                 log.info("Stripe payment successful. Transaction ID: {}", transactionId);

                 // ========== PROPERTY BOOKING LOGIC ==========
                 // If deposit or full payment is successful, mark deposit as paid and book property
                 if (savedPayment.getPaymentType() == DEPOSIT ||
                     savedPayment.getPaymentType() == FULL_AMOUNT) {
                     if (savedPayment.getLease() != null) {
                         Lease paymentLease = savedPayment.getLease();
                         paymentLease.setDepositPaid(true);
                         paymentLease.setStatus(LeaseStatus.ACTIVE); // Activate the lease
                         leaseRepository.save(paymentLease);
                         log.info("Marked deposit as paid and activated lease: {}", paymentLease.getId());
                         
                         // Mark property as unavailable (booked)
                         Property property = paymentLease.getProperty();
                         if (property != null) {
                             property.setAvailable(false);
                             propertyRepository.save(property);
                             log.info("Marked property {} as unavailable (booked)", property.getId());
                         }
                     }
                 }

             } catch (Exception e) {
                 log.error("Failed to initiate Stripe payment: {}", e.getMessage());
                 savedPayment.setStatus(PaymentStatus.FAILED);
                 savedPayment.setNotes("Stripe payment failed: " + e.getMessage());
                 paymentRepository.save(savedPayment);
                 // We might want to rethrow to notify frontend immediately
                 throw new BadRequestException("Card payment failed: " + e.getMessage());
             }
        }
        
        return PaymentResponse.fromEntity(savedPayment);
    }

    @Scheduled(fixedDelay = 300000) // Run every 5 minutes
    public void checkPendingMpesaTransactions() {
        log.info("Checking pending M-Pesa transactions...");
        List<Payment> pendingPayments = paymentRepository.findByStatusAndMethodAndCallbackReceivedFalse(
                PaymentStatus.PROCESSING, PaymentMethod.MPESA);

        for (Payment payment : pendingPayments) {
            if (payment.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {
                log.info("Querying status for old transaction: {}", payment.getTransactionCode());
                queryMpesaTransactionStatus(payment.getTransactionCode());
            }
        }
    }

    private void scheduleTransactionStatusCheck(String checkoutRequestId) {
        log.info("Scheduled status check for: {}", checkoutRequestId);
        // In a real implementation, you might use @Async or a task scheduler
        // For now, we'll rely on the scheduled method above
    }

    @Override
    public String generateTransactionCode() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "PAY-" + timestamp.substring(timestamp.length() - 8) + "-" + random;
    }
}