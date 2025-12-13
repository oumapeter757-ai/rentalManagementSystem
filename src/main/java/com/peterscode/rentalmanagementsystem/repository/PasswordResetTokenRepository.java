package com.peterscode.rentalmanagementsystem.repository;

import com.peterscode.rentalmanagementsystem.model.logs.PasswordResetToken;
import com.peterscode.rentalmanagementsystem.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    @Transactional
    @Modifying
    void deleteByUser(User user);

    @Transactional
    @Modifying
    void deleteByExpiryDateBefore(LocalDateTime dateTime);

    @Transactional
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiryDate < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);
}