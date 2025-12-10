
package com.peterscode.rentalmanagementsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getVerificationUrl(String token) {
        return baseUrl + "/api/auth/verify-email?token=" + token;
    }
}