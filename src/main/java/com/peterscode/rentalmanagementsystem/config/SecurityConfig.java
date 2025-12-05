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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Property Image endpoints - Public read access
                        .requestMatchers(HttpMethod.GET, "/api/properties/*/images").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/properties/*/images/count").permitAll()

                        // Property Image write operations - Landlord and Admin only
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

                        .requestMatchers("/api/payments/mpesa/callback").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/payments").hasAnyRole("TENANT", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/me").hasAnyRole("TENANT", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/*").hasRole("ADMIN")


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
                        .requestMatchers(HttpMethod.POST, "/api/payments").hasAnyRole("TENANT", "ADMIN")

                        // M-Pesa STK Push (Tenant & Admin)
                        .requestMatchers(HttpMethod.POST, "/api/payments/mpesa/stk-push").hasAnyRole("TENANT", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payments/mpesa/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/mpesa/validation").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/mpesa/confirmation").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/payments/me").hasAnyRole("TENANT", "LANDLORD", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/tenant/*").hasAnyRole("LANDLORD", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/payments/status/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/payments/*/status").hasAnyRole("LANDLORD", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/payments/*/mark-paid").hasAnyRole("LANDLORD", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/summary").hasAnyRole("LANDLORD", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/mpesa/pending").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/method/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/payments/*").authenticated()


                        .requestMatchers("/uploads/**").permitAll()

                        // Role-specific endpoints
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/landlord/**").hasAnyRole("LANDLORD", "ADMIN")
                        .requestMatchers("/api/tenant/**").hasAnyRole("TENANT", "ADMIN")

                        // Everything else requires authentication
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