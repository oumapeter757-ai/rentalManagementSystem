package com.peterscode.rentalmanagementsystem.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JwtResponse {
    private String token;
    private String tokenType = "Bearer";
    private String role;
}
