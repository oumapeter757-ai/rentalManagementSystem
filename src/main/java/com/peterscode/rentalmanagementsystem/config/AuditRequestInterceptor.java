package com.peterscode.rentalmanagementsystem.config;

import com.peterscode.rentalmanagementsystem.model.audit.AuditAction;
import com.peterscode.rentalmanagementsystem.model.audit.EntityType;
import com.peterscode.rentalmanagementsystem.service.audit.AuditLogService;
import com.peterscode.rentalmanagementsystem.util.NetworkUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Intercepts ALL HTTP requests to audit critical operations
 * Logs requests with real IP addresses, user agent, and request details
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditRequestInterceptor implements HandlerInterceptor {

    private final AuditLogService auditLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Only audit non-static resources and critical endpoints
        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        // Skip static resources, health checks, and public endpoints
        if (shouldAudit(requestURI, method)) {
            try {
                String ipAddress = NetworkUtil.getClientIp(request);
                String userAgent = request.getHeader("User-Agent");
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();

                String username = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()))
                        ? auth.getName()
                        : "anonymous";

                log.debug("API Request: {} {} from IP: {} by user: {}",
                        method, requestURI, ipAddress, username);

                // Store in request attribute for later use in services
                request.setAttribute("auditedIP", ipAddress);
                request.setAttribute("auditedUserAgent", userAgent);

            } catch (Exception e) {
                log.warn("Failed to audit request: {}", e.getMessage());
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // Log failed requests
        if (ex != null || response.getStatus() >= 400) {
            String requestURI = request.getRequestURI();
            String method = request.getMethod();

            if (shouldAudit(requestURI, method)) {
                try {
                    String ipAddress = NetworkUtil.getClientIp(request);
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";

                    String errorMessage = ex != null ? ex.getMessage() :
                            String.format("HTTP %d - %s", response.getStatus(), getStatusDescription(response.getStatus()));

                    auditLogService.logWithError(
                            AuditAction.SYSTEM_ERROR,
                            EntityType.SYSTEM,
                            null,
                            String.format("%s %s by %s from IP: %s", method, requestURI, username, ipAddress),
                            errorMessage
                    );

                } catch (Exception e) {
                    log.warn("Failed to audit failed request: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Determine if this request should be audited
     */
    private boolean shouldAudit(String uri, String method) {
        // Don't audit static resources
        if (uri.startsWith("/static/") ||
            uri.startsWith("/uploads/") ||
            uri.endsWith(".css") ||
            uri.endsWith(".js") ||
            uri.endsWith(".jpg") ||
            uri.endsWith(".png") ||
            uri.endsWith(".ico")) {
            return false;
        }

        // Don't audit health checks and actuator endpoints (too noisy)
        if (uri.startsWith("/actuator/") || uri.equals("/api/health")) {
            return false;
        }

        // Audit all API endpoints for:
        // - All POST, PUT, DELETE, PATCH (data modification)
        // - Critical GET endpoints (authentication, sensitive data)
        if (method.equals("POST") || method.equals("PUT") ||
            method.equals("DELETE") || method.equals("PATCH")) {
            return true;
        }

        // Audit critical GET endpoints
        if (method.equals("GET") && (
                uri.startsWith("/api/auth/") ||
                uri.startsWith("/api/payments/") ||
                uri.startsWith("/api/admin/") ||
                uri.contains("/sensitive/") ||
                uri.contains("/download/") ||
                uri.contains("/export/"))) {
            return true;
        }

        return false;
    }

    private String getStatusDescription(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable";
            default -> "Error";
        };
    }
}

