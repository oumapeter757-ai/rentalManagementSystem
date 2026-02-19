package com.peterscode.rentalmanagementsystem.scheduler;

import com.peterscode.rentalmanagementsystem.model.booking.Booking;
import com.peterscode.rentalmanagementsystem.model.booking.BookingStatus;
import com.peterscode.rentalmanagementsystem.model.payment.MonthlyPaymentHistory;
import com.peterscode.rentalmanagementsystem.model.payment.PaymentStatus;
import com.peterscode.rentalmanagementsystem.repository.BookingRepository;
import com.peterscode.rentalmanagementsystem.repository.MonthlyPaymentHistoryRepository;
import com.peterscode.rentalmanagementsystem.service.sms.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReminderScheduler {

    private final MonthlyPaymentHistoryRepository monthlyPaymentHistoryRepository;
    private final BookingRepository bookingRepository;
    private final SmsService smsService;

    /**
     * Send reminders for pending payments - runs daily at 9 AM
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void sendPaymentReminders() {
        log.info("Starting payment reminder scheduler...");

        List<MonthlyPaymentHistory> pendingPayments = monthlyPaymentHistoryRepository
                .findPendingPaymentsWithBalance(PaymentStatus.PENDING);

        for (MonthlyPaymentHistory history : pendingPayments) {
            try {
                if (history.getPaymentDeadline() == null) {
                    continue;
                }

                long daysUntilDeadline = ChronoUnit.DAYS.between(LocalDate.now(), history.getPaymentDeadline());

                // Send reminder if deadline is within 7 days
                if (daysUntilDeadline > 0 && daysUntilDeadline <= 7 && history.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                    String propertyTitle = history.getProperty() != null ? history.getProperty().getTitle() : "your property";
                    smsService.sendBalanceReminder(
                            history.getTenant(),
                            history.getBalance().toString()
                    );
                    log.info("Sent payment reminder to tenant: {} for property: {}",
                            history.getTenant().getEmail(), propertyTitle);
                }

                // Send overdue notification
                if (daysUntilDeadline < 0 && history.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                    String message = String.format(
                        "URGENT: Dear %s, your payment of KSh %s is overdue by %d days. Please settle immediately to avoid penalties.",
                        history.getTenant().getFirstName() != null ? history.getTenant().getFirstName() : "Tenant",
                        history.getBalance(),
                        Math.abs(daysUntilDeadline)
                    );
                    smsService.sendReminder(history.getTenant(), message,
                            com.peterscode.rentalmanagementsystem.model.sms.ReminderType.PAYMENT_OVERDUE);
                }
            } catch (Exception e) {
                log.error("Failed to send payment reminder for history {}: {}", history.getId(), e.getMessage());
            }
        }

        log.info("Payment reminder scheduler completed. Processed {} records", pendingPayments.size());
    }

    /**
     * Send reminders for bookings nearing payment deadline - runs daily at 10 AM
     */
    @Scheduled(cron = "0 0 10 * * ?")
    public void sendBookingPaymentDeadlineReminders() {
        log.info("Starting booking payment deadline reminder scheduler...");

        LocalDate today = LocalDate.now();
        LocalDate weekFromNow = today.plusDays(7);

        List<Booking> bookings = bookingRepository.findPendingPaymentsNearDeadline(
                BookingStatus.ACTIVE,
                today,
                weekFromNow
        );

        for (Booking booking : bookings) {
            try {
                if (!booking.isRentPaid() && booking.getPaymentDeadline() != null) {
                    long daysLeft = ChronoUnit.DAYS.between(today, booking.getPaymentDeadline());

                    if (daysLeft >= 0 && daysLeft <= 7) {
                        String propertyTitle = booking.getProperty() != null ? booking.getProperty().getTitle() : "property";
                        BigDecimal rentAmount = booking.getProperty() != null ? booking.getProperty().getRentAmount() : BigDecimal.ZERO;

                        String message = String.format(
                            "Dear %s, you have %d days remaining to complete your rent payment of KSh %s for %s. Deadline: %s",
                            booking.getTenant().getFirstName() != null ? booking.getTenant().getFirstName() : "Tenant",
                            daysLeft,
                            rentAmount,
                            propertyTitle,
                            booking.getPaymentDeadline()
                        );

                        smsService.sendReminder(booking.getTenant(), message,
                                com.peterscode.rentalmanagementsystem.model.sms.ReminderType.PAYMENT_DEADLINE_APPROACHING);

                        log.info("Sent booking payment deadline reminder to tenant: {}", booking.getTenant().getEmail());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to send booking reminder for booking {}: {}", booking.getId(), e.getMessage());
            }
        }

        log.info("Booking payment deadline reminder scheduler completed. Processed {} bookings", bookings.size());
    }

    /**
     * Handle expired bookings - runs daily at midnight
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void handleExpiredBookings() {
        log.info("Starting expired bookings handler...");

        List<Booking> expiredBookings = bookingRepository.findExpiredBookings(BookingStatus.ACTIVE, LocalDate.now());

        for (Booking booking : expiredBookings) {
            try {
                booking.setStatus(BookingStatus.EXPIRED);
                bookingRepository.save(booking);

                // Make property available again
                if (booking.getProperty() != null) {
                    booking.getProperty().setAvailable(true);
                }

                log.info("Marked booking {} as expired for tenant {}", booking.getId(), booking.getTenant().getEmail());
            } catch (Exception e) {
                log.error("Failed to expire booking {}: {}", booking.getId(), e.getMessage());
            }
        }

        log.info("Expired bookings handler completed. Processed {} bookings", expiredBookings.size());
    }
}

