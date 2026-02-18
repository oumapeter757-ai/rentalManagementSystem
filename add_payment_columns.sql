-- Database migration to add new columns for tenant payment system
-- Run this manually if you prefer, or let Hibernate auto-create

-- Add deposit_amount to properties table
ALTER TABLE properties 
ADD COLUMN deposit_amount DECIMAL(19,2) NULL 
COMMENT 'Security deposit amount required for this property';

-- Add deposit_paid to leases table
ALTER TABLE leases 
ADD COLUMN deposit_paid BOOLEAN DEFAULT FALSE 
COMMENT 'Whether tenant has paid the security deposit';

-- Add payment_type to payments table
ALTER TABLE payments 
ADD COLUMN payment_type VARCHAR(20) NULL 
COMMENT 'Type of payment: DEPOSIT, RENT, or FULL_PAYMENT';

-- Verify the changes
SHOW COLUMNS FROM properties LIKE 'deposit_amount';
SHOW COLUMNS FROM leases LIKE 'deposit_paid';
SHOW COLUMNS FROM payments LIKE 'payment_type';
