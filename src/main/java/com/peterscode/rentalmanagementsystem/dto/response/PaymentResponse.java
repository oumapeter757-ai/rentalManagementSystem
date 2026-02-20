package com.peterscode.rentalmanagementsystem.dto.response;

import com.peterscode.rentalmanagementsystem.model.payment.Payment;
import com.peterscode.rentalmanagementsystem.model.property.Property;
import com.peterscode.rentalmanagementsystem.model.lease.Lease;
import com.peterscode.rentalmanagementsystem.model.user.User;
import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String tenantEmail;
    private String phoneNumber;
    private BigDecimal amount;
    private String paymentMethod;
    private String paymentType;
    private String status;
    private String transactionCode;
    private String mpesaReceiptNumber;
    private boolean callbackReceived;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String notes;
    private String gatewayResponseSummary;

    // Nested tenant info for frontend compatibility
    private TenantInfo tenant;
    // Nested property info for frontend compatibility
    private PropertyInfo property;
    // Nested lease info for frontend compatibility
    private LeaseInfo lease;

    @Data
    @Builder
    public static class TenantInfo {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String phoneNumber;
    }

    @Data
    @Builder
    public static class PropertyInfo {
        private Long id;
        private String title;
        private String address;
        private String location;
        private BigDecimal rentAmount;
        private BigDecimal depositAmount;
    }

    @Data
    @Builder
    public static class LeaseInfo {
        private Long id;
        private PropertyInfo property;
    }

    public static PaymentResponse fromEntity(Payment payment) {
        // Never expose raw gateway/callback JSON to the frontend
        User tenantUser = payment.getTenant();
        Property prop = payment.getProperty();
        Lease leaseEntity = payment.getLease();

        // Build tenant info
        String resolvedFirst = resolveName(tenantUser.getFirstName(), tenantUser.getUsername(), tenantUser.getEmail());
        String resolvedLast = tenantUser.getLastName() != null ? tenantUser.getLastName() : "";
        TenantInfo tenantInfo = TenantInfo.builder()
                .id(tenantUser.getId())
                .firstName(resolvedFirst)
                .lastName(resolvedLast)
                .email(tenantUser.getEmail())
                .phoneNumber(tenantUser.getPhoneNumber())
                .build();

        // Build property info
        PropertyInfo propertyInfo = null;
        if (prop != null) {
            propertyInfo = PropertyInfo.builder()
                    .id(prop.getId())
                    .title(prop.getTitle())
                    .address(prop.getAddress())
                    .location(prop.getLocation())
                    .rentAmount(prop.getRentAmount())
                    .depositAmount(prop.getDepositAmount())
                    .build();
        }

        // Build lease info (with nested property if lease has one)
        LeaseInfo leaseInfo = null;
        if (leaseEntity != null) {
            PropertyInfo leasePropertyInfo = null;
            if (leaseEntity.getProperty() != null) {
                Property leaseProp = leaseEntity.getProperty();
                leasePropertyInfo = PropertyInfo.builder()
                        .id(leaseProp.getId())
                        .title(leaseProp.getTitle())
                        .address(leaseProp.getAddress())
                        .location(leaseProp.getLocation())
                        .rentAmount(leaseProp.getRentAmount())
                        .depositAmount(leaseProp.getDepositAmount())
                        .build();
            }
            leaseInfo = LeaseInfo.builder()
                    .id(leaseEntity.getId())
                    .property(leasePropertyInfo != null ? leasePropertyInfo : propertyInfo)
                    .build();
        }

        // Determine property title for flat field
        String propertyTitle = null;
        if (prop != null) {
            propertyTitle = prop.getTitle();
        } else if (leaseEntity != null && leaseEntity.getProperty() != null) {
            propertyTitle = leaseEntity.getProperty().getTitle();
        }

        return PaymentResponse.builder()
                .id(payment.getId())
                .tenantId(tenantUser.getId())
                .tenantName(resolvedFirst + " " + resolvedLast)
                .tenantEmail(tenantUser.getEmail())
                .phoneNumber(payment.getPhoneNumber() != null ? payment.getPhoneNumber() : tenantUser.getPhoneNumber())
                .amount(payment.getAmount())
                .paymentMethod(payment.getMethod().name())
                .paymentType(payment.getPaymentType() != null ? payment.getPaymentType().name() : null)
                .status(payment.getStatus().name())
                .transactionCode(payment.getTransactionCode())
                .mpesaReceiptNumber(payment.getMerchantRequestID())
                .callbackReceived(payment.isCallbackReceived())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .notes(sanitizeNotes(payment.getNotes()))
                .gatewayResponseSummary(null)
                .tenant(tenantInfo)
                .property(propertyInfo)
                .lease(leaseInfo)
                .build();
    }


    /**
     * Filter out internal/technical notes that should not be shown to users.
     */
    private static String sanitizeNotes(String notes) {
        if (notes == null || notes.isEmpty()) return null;
        if (notes.startsWith("{") || notes.startsWith("ws_") || notes.startsWith("Pending M-Pesa")) {
            return null;
        }
        return notes;
    }

    private static String resolveName(String firstName, String username, String email) {
        if (firstName != null && !firstName.isBlank()) return firstName;
        if (username != null && !username.isBlank()) return username;
        if (email != null) return email.split("@")[0];
        return "User";
    }
}