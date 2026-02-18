-- V18: Add tenant payment system columns
-- Date: 2026-02-09
-- Description: Adds deposit_amount to properties, deposit_paid to leases, and payment_type to payments

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

-- Optional: Update existing properties with default deposit (2 months rent)
-- UPDATE properties SET deposit_amount = rent * 2 WHERE deposit_amount IS NULL;
