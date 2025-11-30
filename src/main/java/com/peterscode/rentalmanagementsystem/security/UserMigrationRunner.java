package com.peterscode.rentalmanagementsystem.security;

import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserMigrationRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        userRepository.findAll().forEach(user -> {
            boolean changed = false;

            // Normalize email and username
            String email = user.getEmail();
            String username = user.getUsername();
            if (!email.equals(email.toLowerCase().trim())) {
                user.setEmail(email.toLowerCase().trim());
                changed = true;
            }
            if (!username.equals(username.toLowerCase().trim())) {
                user.setUsername(username.toLowerCase().trim());
                changed = true;
            }

            // Re-encode password if not already BCrypt (detect via $2a$ prefix)
            if (!user.getPassword().startsWith("$2a$")) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
                changed = true;
            }

            // Ensure enabled is true (optional)
            if (!user.isEnabled()) {
                user.setEnabled(true);
                changed = true;
            }

            if (changed) {
                userRepository.save(user);
                System.out.println("Updated user: " + user.getEmail());
            }
        });
        System.out.println("User migration completed.");
    }
}
