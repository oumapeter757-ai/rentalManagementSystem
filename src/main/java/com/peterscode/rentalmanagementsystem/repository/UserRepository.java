// src/main/java/com/peterscode/rentalmanagementsystem/repository/UserRepository.java
package com.peterscode.rentalmanagementsystem.repository;

import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);


    List<User> findByRole(Role role);


    List<User> findByRoleAndEnabled(Role role, boolean enabled);
}