package com.peterscode.rentalmanagementsystem.aspect;

import com.peterscode.rentalmanagementsystem.model.audit.AuditAction;
import com.peterscode.rentalmanagementsystem.model.audit.AuditLog;
import com.peterscode.rentalmanagementsystem.model.audit.EntityType;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.AuditLogRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * AOP Aspect that automatically audits ALL controller method calls.
 * Captures: user identity, IP address, device info, action, entity, and outcome.
 * This ensures every API call is tracked — logins, payments, DB changes, views, etc.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Intercepts every public method in any @RestController under the controller package.
     * Excludes the AuditLogController itself to avoid infinite recursion.
     */
    @Around("execution(* com.peterscode.rentalmanagementsystem.controller..*(..)) " +
            "&& !execution(* com.peterscode.rentalmanagementsystem.controller.AuditLogController.*(..))")
    public Object auditControllerCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        // Extract HTTP method and path
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String httpMethod = resolveHttpMethod(method);
        String endpoint = resolveEndpoint(joinPoint.getTarget().getClass(), method);

        // Extract request info
        String ipAddress = null;
        String userAgent = null;
        String requestUri = null;
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                ipAddress = getClientIp(request);
                userAgent = request.getHeader("User-Agent");
                requestUri = request.getMethod() + " " + request.getRequestURI();
            }
        } catch (Exception e) {
            log.debug("Could not extract request info: {}", e.getMessage());
        }

        // Extract user
        String username = null;
        User userEntity = null;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String email = auth.getName();
                username = email;
                Optional<User> userOpt = userRepository.findByEmail(email);
                if (userOpt.isPresent()) {
                    userEntity = userOpt.get();
                    username = userEntity.getFirstName() + " " + userEntity.getLastName() + " (" + email + ")";
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract user: {}", e.getMessage());
        }

        // Determine action and entity type
        AuditAction action = resolveAction(httpMethod, className, methodName);
        EntityType entityType = resolveEntityType(className);

        // Extract entity ID from method arguments if present
        Long entityId = extractEntityId(joinPoint.getArgs(), signature.getParameterNames());

        Object result = null;
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            status = "FAILURE";
            errorMessage = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            throw ex;
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Build detailed description
            String details = buildDetails(requestUri, httpMethod, className, methodName,
                    duration, username, entityId);

            try {
                AuditLog auditLog = AuditLog.builder()
                        .action(action)
                        .entityType(entityType)
                        .entityId(entityId)
                        .details(details)
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .status(status)
                        .errorMessage(errorMessage)
                        .build();

                if (userEntity != null) {
                    auditLog.setUser(userEntity);
                    auditLog.setUsername(username);
                } else if (username != null) {
                    auditLog.setUsername(username);
                } else {
                    auditLog.setUsername("anonymous");
                }

                auditLogRepository.save(auditLog);
            } catch (Exception e) {
                log.error("Failed to save audit log for {}.{}: {}", className, methodName, e.getMessage());
            }
        }
    }

    // ── Helper methods ────────────────────────────────────────────────

    private String buildDetails(String requestUri, String httpMethod, String className,
                                String methodName, long duration, String username, Long entityId) {
        StringBuilder sb = new StringBuilder();
        sb.append(requestUri != null ? requestUri : httpMethod + " " + className + "." + methodName);
        if (entityId != null) {
            sb.append(" | Entity ID: ").append(entityId);
        }
        if (username != null) {
            sb.append(" | User: ").append(username);
        }
        sb.append(" | Duration: ").append(duration).append("ms");
        return sb.toString();
    }

    private AuditAction resolveAction(String httpMethod, String className, String methodName) {
        String lcMethod = methodName.toLowerCase();

        // Auth-specific
        if (className.contains("Auth")) {
            if (lcMethod.contains("login")) return AuditAction.LOGIN;
            if (lcMethod.contains("logout")) return AuditAction.LOGOUT;
            if (lcMethod.contains("register") || lcMethod.contains("createuser")) return AuditAction.REGISTER;
            if (lcMethod.contains("verify")) return AuditAction.EMAIL_VERIFY;
            if (lcMethod.contains("reset")) return AuditAction.PASSWORD_RESET;
            if (lcMethod.contains("forgot")) return AuditAction.PASSWORD_RESET;
        }

        // Payment-specific
        if (className.contains("Payment") || className.contains("Tenant")) {
            if (lcMethod.contains("initiate") || lcMethod.contains("stk")) return AuditAction.PAYMENT_INITIATE;
            if (lcMethod.contains("refund")) return AuditAction.PAYMENT_REFUND;
            if (lcMethod.contains("reverse")) return AuditAction.PAYMENT_REVERSE;
            if (lcMethod.contains("callback") || lcMethod.contains("confirm")) return AuditAction.PAYMENT_SUCCESS;
        }

        // Lease-specific
        if (className.contains("Lease")) {
            if (lcMethod.contains("terminate")) return AuditAction.LEASE_TERMINATE;
            if (lcMethod.contains("renew")) return AuditAction.LEASE_RENEW;
            if (lcMethod.contains("activate")) return AuditAction.LEASE_ACTIVATE;
        }

        // Application-specific
        if (className.contains("Application")) {
            if (lcMethod.contains("submit") || lcMethod.contains("apply") || "POST".equals(httpMethod))
                return AuditAction.APPLICATION_SUBMIT;
            if (lcMethod.contains("status")) return AuditAction.APPLICATION_APPROVE;
        }

        // Maintenance-specific
        if (className.contains("Maintenance")) {
            if (lcMethod.contains("assign")) return AuditAction.MAINTENANCE_ASSIGN;
            if (lcMethod.contains("complete")) return AuditAction.MAINTENANCE_COMPLETE;
        }

        // Message-specific
        if (className.contains("Message")) {
            if ("POST".equals(httpMethod)) return AuditAction.SEND_MESSAGE;
            if (lcMethod.contains("read")) return AuditAction.READ_MESSAGE;
        }

        // Property-specific
        if (className.contains("Property") && lcMethod.contains("publish")) return AuditAction.PROPERTY_PUBLISH;

        // Booking-specific
        if (className.contains("Booking") && "POST".equals(httpMethod)) return AuditAction.PROPERTY_BOOK;

        // User-specific
        if (className.contains("User")) {
            if (lcMethod.contains("status") || lcMethod.contains("disable")) return AuditAction.USER_DISABLE;
            if (lcMethod.contains("enable")) return AuditAction.USER_ENABLE;
        }

        // Generic CRUD fallback
        return switch (httpMethod) {
            case "POST" -> AuditAction.CREATE;
            case "PUT", "PATCH" -> AuditAction.UPDATE;
            case "DELETE" -> AuditAction.DELETE;
            default -> AuditAction.VIEW;
        };
    }

    private EntityType resolveEntityType(String className) {
        if (className.contains("Auth") || className.contains("User")) return EntityType.USER;
        if (className.contains("Property")) return EntityType.PROPERTY;
        if (className.contains("Lease")) return EntityType.LEASE;
        if (className.contains("Payment") || className.contains("Tenant")) return EntityType.PAYMENT;
        if (className.contains("Application")) return EntityType.APPLICATION;
        if (className.contains("Maintenance")) return EntityType.MAINTENANCE_REQUEST;
        if (className.contains("Message")) return EntityType.MESSAGE;
        if (className.contains("Booking")) return EntityType.BOOKING;
        if (className.contains("Sms")) return EntityType.SYSTEM;
        return EntityType.SYSTEM;
    }

    private String resolveHttpMethod(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) return "GET";
        if (method.isAnnotationPresent(PostMapping.class)) return "POST";
        if (method.isAnnotationPresent(PutMapping.class)) return "PUT";
        if (method.isAnnotationPresent(DeleteMapping.class)) return "DELETE";
        if (method.isAnnotationPresent(PatchMapping.class)) return "PATCH";
        return "UNKNOWN";
    }

    private String resolveEndpoint(Class<?> controllerClass, Method method) {
        StringBuilder sb = new StringBuilder();

        RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
        if (classMapping != null && classMapping.value().length > 0) {
            sb.append(classMapping.value()[0]);
        }

        String[] paths = {};
        if (method.isAnnotationPresent(GetMapping.class)) paths = method.getAnnotation(GetMapping.class).value();
        else if (method.isAnnotationPresent(PostMapping.class)) paths = method.getAnnotation(PostMapping.class).value();
        else if (method.isAnnotationPresent(PutMapping.class)) paths = method.getAnnotation(PutMapping.class).value();
        else if (method.isAnnotationPresent(DeleteMapping.class)) paths = method.getAnnotation(DeleteMapping.class).value();
        else if (method.isAnnotationPresent(PatchMapping.class)) paths = method.getAnnotation(PatchMapping.class).value();

        if (paths.length > 0) {
            sb.append(paths[0]);
        }

        return sb.toString();
    }

    private Long extractEntityId(Object[] args, String[] paramNames) {
        if (args == null || paramNames == null) return null;

        for (int i = 0; i < paramNames.length; i++) {
            String name = paramNames[i].toLowerCase();
            if ((name.contains("id") && !name.contains("sid")) && args[i] instanceof Long) {
                return (Long) args[i];
            }
            if ((name.contains("id") && !name.contains("sid")) && args[i] instanceof Integer) {
                return ((Integer) args[i]).longValue();
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR", "HTTP_CLIENT_IP", "HTTP_FORWARDED"
        };
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}

