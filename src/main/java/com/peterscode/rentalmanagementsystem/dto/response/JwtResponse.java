package com.peterscode.rentalmanagementsystem.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JwtResponse {
    private String token;
    private String tokenType;
    private String role;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String username;
    private String phoneNumber;
}
