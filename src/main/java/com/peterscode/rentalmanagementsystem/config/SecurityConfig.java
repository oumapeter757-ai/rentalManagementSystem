package com.peterscode.rentalmanagementsystem.config;

import com.peterscode.rentalmanagementsystem.security.JwtService;
import com.peterscode.rentalmanagementsystem.security.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtAuthFilter jwtFilter = new JwtAuthFilter(jwtService, userDetailsService);

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
// Public endpoints
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers("/api/public/**").permitAll()
                                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

// User management endpoints (Admin only)
                                .requestMatchers(HttpMethod.GET, "/api/users/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/users/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/users/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.PATCH, "/api/users/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN")

// Auth endpoints (Public)
                                .requestMatchers(HttpMethod.POST, "/api/auth/register/admin").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/auth/register/tenant").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/auth/register/landlord").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/auth/verify-email").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/auth/resend-verification").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()

// Property Image endpoints
                                .requestMatchers(HttpMethod.GET, "/api/properties/*/images").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/properties/*/images/count").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/properties/*/images").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/properties/*/images/multiple").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/properties/*/images").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/properties/*/images/*").hasAnyRole("LANDLORD", "ADMIN")

// Property CRUD operations
                                .requestMatchers(HttpMethod.POST, "/api/properties").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/properties/*").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/properties/*").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/properties").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/properties/*").permitAll()

// Payment endpoints
                                .requestMatchers(HttpMethod.POST, "/api/payments").hasAnyRole("TENANT", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/payments/me").hasAnyRole("TENANT", "LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/payments/{id}").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/payments/transaction/{transactionCode}").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/payments").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/payments/status/{status}").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/payments/method/{method}").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/payments/mpesa/pending").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/payments/summary").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/payments/revenue/total").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/payments/revenue/tenant/{tenantId}").hasAnyRole("ADMIN", "LANDLORD")
                                .requestMatchers(HttpMethod.GET, "/api/payments/{id}/success").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/payments/{id}/pending").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/payments/generate-transaction-code").hasAnyRole("ADMIN", "LANDLORD")
                                .requestMatchers(HttpMethod.GET, "/api/payments/tenant/{tenantId}").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/payments/{id}").hasAnyRole("ADMIN", "LANDLORD")
                                .requestMatchers(HttpMethod.PUT, "/api/payments/{id}/status").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/payments/{id}/mark-paid").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/payments/{id}/reverse").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/payments/{id}/refund").hasAnyRole("ADMIN", "LANDLORD")
                                .requestMatchers(HttpMethod.POST, "/api/payments/mpesa/query-status").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/payments/{id}").authenticated()

// M-Pesa endpoints
                                .requestMatchers(HttpMethod.POST, "/api/payments/mpesa/stk-push").hasAnyRole("TENANT", "ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/payments/mpesa/callback").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/payments/mpesa/validation").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/payments/mpesa/confirmation").permitAll()

// Lease endpoints
                                .requestMatchers(HttpMethod.POST, "/api/leases").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/leases/me").hasAnyRole("TENANT", "LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/leases/property/*").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/leases/property/*/active").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/leases/*").authenticated()
                                .requestMatchers(HttpMethod.PUT, "/api/leases/*/terminate").hasAnyRole("LANDLORD", "ADMIN", "TENANT")

// Rental Application endpoints
                                .requestMatchers(HttpMethod.POST, "/api/applications").hasAnyRole("TENANT", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/applications/my-applications").hasAnyRole("TENANT", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/applications/my-properties").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/applications/property/*").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/applications/status/*").hasAnyRole("TENANT", "LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/applications/*/status").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/applications/*/cancel").hasAnyRole("TENANT", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/applications/count/status/*").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/applications/pending").hasAnyRole("LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/applications/*").hasAnyRole("TENANT", "LANDLORD", "ADMIN")
                                .requestMatchers(HttpMethod.GET, "/api/applications").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/applications/*").hasRole("ADMIN")

// Maintenance endpoints
                                .requestMatchers(HttpMethod.POST, "/api/maintenance").hasRole("TENANT")
                                .requestMatchers(HttpMethod.POST, "/api/maintenance/*/images").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/maintenance").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/maintenance/me").hasRole("TENANT")
                                .requestMatchers(HttpMethod.GET, "/api/maintenance/{id}").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/maintenance/status/{status}").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/maintenance/category/{category}").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/maintenance/priority/{priority}").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/maintenance/open").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/maintenance/open/count").authenticated()
                                .requestMatchers(HttpMethod.PUT, "/api/maintenance/{id}").authenticated()
                                .requestMatchers(HttpMethod.PUT, "/api/maintenance/{id}/status").hasAnyRole("ADMIN", "LANDLORD")
                                .requestMatchers(HttpMethod.PUT, "/api/maintenance/{id}/assign").hasAnyRole("ADMIN", "LANDLORD")
                                .requestMatchers(HttpMethod.PUT, "/api/maintenance/{id}/notes").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/maintenance/summary").hasAnyRole("ADMIN", "LANDLORD")
                                .requestMatchers(HttpMethod.GET, "/api/maintenance/summary/me").hasRole("TENANT")
                                .requestMatchers(HttpMethod.DELETE, "/api/maintenance/{id}").authenticated()
                                .requestMatchers(HttpMethod.DELETE, "/api/maintenance/{requestId}/images/{imageId}").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/maintenance/{id}/accessible").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/maintenance/{id}/can-update").authenticated()

                        .requestMatchers(
                                "/",
                                "/api/health",                    // Add this line
                                "/api/files/health",              // Your existing endpoint
                                "/actuator/health",
                                "/api/auth/**",
                                "/api/files/**",
                                "/uploads/**",
                                "/static/**",
                                "/swagger-ui/**",                 // Now this will work
                                "/v3/api-docs/**",                // Now this will work
                                "/error"
                        ).permitAll()

                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/api/**").authenticated()


                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/landlord/**").hasAnyRole("LANDLORD", "ADMIN")
                        .requestMatchers("/api/tenant/**").hasAnyRole("TENANT", "ADMIN")


                        .anyRequest().authenticated()
                )


                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Component
    static class JwtAuthFilter extends OncePerRequestFilter {

        private final JwtService jwtService;
        private final UserDetailsServiceImpl userDetailsService;

        JwtAuthFilter(JwtService jwtService, UserDetailsServiceImpl userDetailsService) {
            this.jwtService = jwtService;
            this.userDetailsService = userDetailsService;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws IOException, jakarta.servlet.ServletException {

            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                if (jwtService.validate(token)) {
                    String email = jwtService.getEmail(token).trim().toLowerCase();


                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    var auth = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()

                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
            chain.doFilter(request, response);
        }
    }


}