package com.peterscode.rentalmanagementsystem.model.audit;

public enum AuditAction {
    // Authentication
    LOGIN,
    LOGOUT,
    REGISTER,
    PASSWORD_CHANGE,
    EMAIL_VERIFY,
    PASSWORD_RESET,
    
    // CRUD Operations
    CREATE,
    UPDATE,
    DELETE,
    VIEW,
    
    // Payment Operations
    PAYMENT_INITIATE,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    PAYMENT_REFUND,
    PAYMENT_REVERSE,
    
    // Lease Operations
    LEASE_ACTIVATE,
    LEASE_TERMINATE,
    LEASE_RENEW,
    DEPOSIT_PAID,
    
    // Application Operations
    APPLICATION_SUBMIT,
    APPLICATION_APPROVE,
    APPLICATION_REJECT,
    
    // Property Operations
    PROPERTY_PUBLISH,
    PROPERTY_UNPUBLISH,
    PROPERTY_BOOK,
    
    // Maintenance Operations
    MAINTENANCE_ASSIGN,
    MAINTENANCE_COMPLETE,
    
    // Admin Operations
    USER_DISABLE,
    USER_ENABLE,
    ROLE_CHANGE,
    
    // System Operations
    SYSTEM_ERROR,
    ACCESS_DENIED
}
