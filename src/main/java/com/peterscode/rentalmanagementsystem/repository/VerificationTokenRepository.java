package com.peterscode.rentalmanagementsystem.repository;



import com.peterscode.rentalmanagementsystem.model.logs.VerificationToken;
import com.peterscode.rentalmanagementsystem.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByToken(String token);

    void deleteByUser(User user);


}