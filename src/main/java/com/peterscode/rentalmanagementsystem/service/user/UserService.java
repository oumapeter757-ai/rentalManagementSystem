
package com.peterscode.rentalmanagementsystem.service.user;

import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;

import java.util.List;

public interface UserService {
    List<User> getAllUsers();
    User getUserById(Long id);
    List<User> getUsersByRole(Role role);
    User updateUser(Long id, User userDetails);
    void deleteUser(Long id);
    User toggleUserStatus(Long id, boolean enabled);
}