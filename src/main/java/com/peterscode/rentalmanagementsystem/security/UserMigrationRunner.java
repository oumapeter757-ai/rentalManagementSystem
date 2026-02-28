package com.peterscode.rentalmanagementsystem.security;

import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserMigrationRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        try {
            long userCount = userRepository.count();
            if (userCount == 0) {
                log.info("No users found in database. Migration skipped.");
                return;
            }

            log.info("Starting user migration for {} users...", userCount);

            // Use AtomicInteger for mutable counter in lambda
            AtomicInteger updatedCount = new AtomicInteger(0);

            userRepository.findAll().forEach(user -> {
                boolean changed = processUser(user);

                if (changed) {
                    userRepository.save(user);
                    updatedCount.incrementAndGet();
                    log.info("Updated user: {}", user.getEmail());
                }
            });

            log.info("User migration completed. Updated {} out of {} users.",
                    updatedCount.get(), userCount);

        } catch (Exception e) {
            log.error("Error during user migration: {}", e.getMessage(), e);
        }
    }

    private boolean processUser(User user) {
        boolean changed = false;

        // Normalize email and username
        String email = user.getEmail();
        String username = user.getUsername();

        // Normalize email
        if (email != null && !email.equals(email.toLowerCase().trim())) {
            String normalizedEmail = email.toLowerCase().trim();
            log.debug("Normalizing email: {} -> {}", email, normalizedEmail);
            user.setEmail(normalizedEmail);
            changed = true;
        }

        // Normalize username
        if (username != null && !username.equals(username.toLowerCase().trim())) {
            String normalizedUsername = username.toLowerCase().trim();
            log.debug("Normalizing username: {} -> {}", username, normalizedUsername);
            user.setUsername(normalizedUsername);
            changed = true;
        }

        // Re-encode password if not already BCrypt (detect via $2a$ or $2b$ prefix)
        String password = user.getPassword();
        if (password != null && !password.startsWith("$2a$") && !password.startsWith("$2b$")) {
            log.debug("Re-encoding password for user: {}", user.getEmail());
            user.setPassword(passwordEncoder.encode(password));
            changed = true;
        }

        // Do NOT auto-enable users — they must verify their email first
        // if (!user.isEnabled()) {
        // user.setEnabled(true);
        // changed = true;
        // }

        return changed;
    }
}