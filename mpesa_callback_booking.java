// ADD THIS CODE TO PaymentServiceImpl.java in the processMpesaCallback method
// After setting payment status to SUCCESSFUL, add:

        // ========== PROPERTY BOOKING LOGIC ==========
        // If deposit or full payment is successful, mark deposit as paid and book property
        if (payment.getPaymentType() == PaymentType.DEPOSIT || 
            payment.getPaymentType() == PaymentType.FULL_PAYMENT) {
            if (payment.getLease() != null) {
                Lease lease = payment.getLease();
                lease.setDepositPaid(true);
                leaseRepository.save(lease);
                log.info("Marked deposit as paid for lease: {}", lease.getId());
                
                // Mark property as unavailable (booked)
                Property property = lease.getProperty();
                property.setAvailable(false);
                propertyRepository.save(property);
                log.info("Marked property {} as unavailable (booked)", property.getId());
            }
        }

// NOTE: You'll need to add PropertyRepository as a dependency:
// - Add to class fields: private final PropertyRepository propertyRepository;
// - Spring will auto-inject via @RequiredArgsConstructor
