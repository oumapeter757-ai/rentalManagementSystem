package com.peterscode.rentalmanagementsystem.service.auth;


import com.peterscode.rentalmanagementsystem.repository.PasswordResetTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupService {
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Scheduled(cron = "0 0 2 * * ?") // Runs daily at 2 AM
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Cleaning up expired password reset tokens...");
        int deleted = passwordResetTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        log.info("Deleted {} expired password reset tokens", deleted);
    }
}
