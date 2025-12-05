package com.peterscode.rentalmanagementsystem.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peterscode.rentalmanagementsystem.dto.request.MpesaStkRequest;
import com.peterscode.rentalmanagementsystem.dto.request.PaymentRequest;
import com.peterscode.rentalmanagementsystem.dto.response.MpesaStkResponse;
import com.peterscode.rentalmanagementsystem.dto.response.PaymentResponse;
import com.peterscode.rentalmanagementsystem.dto.response.PaymentSummaryResponse;
import com.peterscode.rentalmanagementsystem.exception.BadRequestException;
import com.peterscode.rentalmanagementsystem.exception.ResourceNotFoundException;
import com.peterscode.rentalmanagementsystem.model.payment.Payment;
import com.peterscode.rentalmanagementsystem.model.payment.PaymentMethod;
import com.peterscode.rentalmanagementsystem.model.payment.PaymentStatus;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.PaymentRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;

import com.peterscode.rentalmanagementsystem.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final MpesaService mpesaService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, String callerEmail) {
        log.info("Creating payment for tenant: {}", request.getTenantId());

        // Validate tenant exists
        User tenant = userRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + request.getTenantId()));

        // Validate caller permissions
        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!caller.getId().equals(tenant.getId()) && caller.getRole() != Role.ADMIN) {
            throw new BadRequestException("You can only create payments for yourself");
        }

        // Validate M-Pesa phone number if method is MPESA
        if (request.getPaymentMethod().equals("MPESA") &&
                (request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty())) {
            throw new BadRequestException("Phone number is required for M-Pesa payments");
        }

        // Format phone number if provided
        String formattedPhone = null;
        if (request.getPhoneNumber() != null) {
            formattedPhone = mpesaService.formatPhoneNumber(request.getPhoneNumber());
        }

        // Create payment entity
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

        // Auto-complete cash payments
        if (payment.getMethod() == PaymentMethod.CASH) {
            payment.setStatus(PaymentStatus.SUCCESSFUL);
            payment.setPaidAt(LocalDateTime.now());
        }

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment created with reference: {}", savedPayment.getTransactionCode());

        return PaymentResponse.fromEntity(savedPayment);
    }

    @Override
    @Transactional
    public MpesaStkResponse initiateMpesaPayment(MpesaStkRequest request, String callerEmail) {
        log.info("Initiating M-Pesa STK Push for tenant: {}", request.getTenantId());

        // Validate tenant
        User tenant = userRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        // Validate caller permissions
        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!caller.getId().equals(tenant.getId()) && caller.getRole() != Role.ADMIN) {
            throw new BadRequestException("You can only initiate payments for yourself");
        }

        // Format phone number
        String phoneNumber = mpesaService.formatPhoneNumber(request.getPhoneNumber());

        try {
            // Generate account reference
            String accountReference = "RENT-" + tenant.getId() + "-" + System.currentTimeMillis();
            String description = request.getDescription() != null ?
                    request.getDescription() : "Rent payment for " + tenant.getFirstName();

            // Initiate STK Push
            MpesaStkResponse stkResponse = mpesaService.initiateStkPush(
                    phoneNumber,
                    request.getAmount(),
                    accountReference,
                    description
            );

            // Check if STK Push was initiated successfully
            if (stkResponse != null && stkResponse.isSuccessful()) {
                // Create payment record
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
                log.info("M-Pesa STK Push initiated successfully. CheckoutRequestID: {}",
                        stkResponse.getCheckoutRequestID());

                return stkResponse;
            } else {
                String errorMsg = stkResponse != null ?
                        stkResponse.getResponseDescription() : "No response from M-Pesa";
                log.error("Failed to initiate M-Pesa STK Push: {}", errorMsg);
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
        log.info("Processing M-Pesa callback");

        try {
            // Parse callback data
            Map<String, Object> callback = objectMapper.readValue(callbackData, Map.class);
            Map<String, Object> body = (Map<String, Object>) callback.get("Body");
            Map<String, Object> stkCallback = (Map<String, Object>) body.get("stkCallback");

            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");
            String resultCode = stkCallback.get("ResultCode").toString();
            String resultDesc = (String) stkCallback.get("ResultDesc");

            log.info("Processing callback for CheckoutRequestID: {}, ResultCode: {}, ResultDesc: {}",
                    checkoutRequestId, resultCode, resultDesc);

            // Find payment by checkout request ID
            Optional<Payment> paymentOpt = paymentRepository.findByTransactionCode(checkoutRequestId);

            if (paymentOpt.isPresent()) {
                Payment payment = paymentOpt.get();

                // Update payment based on result
                if ("0".equals(resultCode)) {
                    // Successful payment
                    Map<String, Object> callbackMetadata = (Map<String, Object>) stkCallback.get("CallbackMetadata");
                    if (callbackMetadata != null) {
                        List<Map<String, Object>> items = (List<Map<String, Object>>) callbackMetadata.get("Item");

                        String mpesaReceiptNumber = null;
                        String phoneNumber = null;
                        BigDecimal amount = null;

                        for (Map<String, Object> item : items) {
                            String name = (String) item.get("Name");
                            Object value = item.get("Value");

                            switch (name) {
                                case "MpesaReceiptNumber":
                                    mpesaReceiptNumber = value.toString();
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
                        payment.setTransactionCode(mpesaReceiptNumber);
                        payment.setPhoneNumber(phoneNumber);
                        if (amount != null) {
                            payment.setAmount(amount);
                        }
                        payment.setPaidAt(LocalDateTime.now());
                        payment.setGatewayResponse(callbackData);
                        log.info("M-Pesa payment successful. Receipt: {}", mpesaReceiptNumber);
                    }
                } else {
                    // Failed payment
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setGatewayResponse(callbackData);
                    log.warn("M-Pesa payment failed: {}", resultDesc);
                }

                payment.setCallbackReceived(true);
                paymentRepository.save(payment);

                log.info("Payment {} updated with status: {}", payment.getId(), payment.getStatus());

            } else {
                log.error("Payment not found for checkout request ID: {}", checkoutRequestId);
            }

        } catch (Exception e) {
            log.error("Error processing M-Pesa callback: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process M-Pesa callback", e);
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

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(PaymentResponse::fromEntity)
                .collect(Collectors.toList());
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
        return paymentRepository.findPendingMpesaCallbacks().stream()
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

            // Update status
            payment.setStatus(newStatus);

            // Set paid timestamp if status changed to SUCCESSFUL
            if (newStatus == PaymentStatus.SUCCESSFUL && oldStatus != PaymentStatus.SUCCESSFUL) {
                payment.setPaidAt(LocalDateTime.now());
            }

            // Update notes if provided
            if (notes != null && !notes.trim().isEmpty()) {
                payment.setNotes(notes);
            }

            Payment updatedPayment = paymentRepository.save(payment);
            log.info("Payment {} status updated from {} to {}",
                    payment.getTransactionCode(), oldStatus, newStatus);

            return PaymentResponse.fromEntity(updatedPayment);

        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid payment status: " + status);
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

        // Update payment
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setPaidAt(LocalDateTime.now());

        if (transactionCode != null && !transactionCode.trim().isEmpty()) {
            payment.setTransactionCode(transactionCode);
        }

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
                .filter(p -> p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.PROCESSING)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalTransactions = allPayments.size();
        long successfulTransactions = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESSFUL)
                .count();
        long pendingTransactions = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.PROCESSING)
                .count();
        long failedTransactions = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED)
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
    public String generateTransactionCode() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "PAY-" + timestamp.substring(timestamp.length() - 8) + "-" + random;
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
}