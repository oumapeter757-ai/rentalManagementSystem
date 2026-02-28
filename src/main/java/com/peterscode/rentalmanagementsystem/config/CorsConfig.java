package com.peterscode.rentalmanagementsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

        @Value("${app.cors.allowed-origins}")
        private String allowedOrigins;

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();

                // Parse comma-separated origins from env var
                List<String> origins = Arrays.asList(allowedOrigins.split(","));
                configuration.setAllowedOrigins(origins.stream().map(String::trim).toList());

                configuration.setAllowedMethods(Arrays.asList(
                                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));

                configuration.setAllowedHeaders(Arrays.asList(
                                "Authorization",
                                "Content-Type",
                                "Accept",
                                "Origin",
                                "X-Requested-With",
                                "Access-Control-Request-Method",
                                "Access-Control-Request-Headers",
                                "X-CSRF-Token"));

                configuration.setExposedHeaders(Arrays.asList(
                                "Authorization",
                                "Content-Disposition",
                                "Access-Control-Allow-Origin",
                                "Access-Control-Allow-Credentials"));

                configuration.setAllowCredentials(true);
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        public CorsFilter corsFilter() {
                return new CorsFilter(corsConfigurationSource());
        }
}