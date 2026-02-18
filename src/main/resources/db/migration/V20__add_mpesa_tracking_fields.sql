-- Add M-Pesa tracking fields to payments table
ALTER TABLE payments 
ADD COLUMN checkout_request_id VARCHAR(100),
ADD COLUMN merchant_request_id VARCHAR(100);
