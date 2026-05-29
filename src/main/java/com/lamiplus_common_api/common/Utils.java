package com.lamiplus_common_api.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class Utils {



    public static String getTenantIdFromContext() {
        log.debug("getTenantIdFromContext() called");

        try {
            Class<?> tenantContextClass = Class.forName("coreapplication.service.plugin_manager.TenantContext");
            java.lang.reflect.Method method = tenantContextClass.getMethod("getTenantId");
            String tenantId = (String) method.invoke(null);

            if (tenantId != null && !tenantId.isEmpty()) {
                log.debug("Tenant ID from core context: {}", tenantId);
                return tenantId;
            }
        } catch (ClassNotFoundException e) {
            log.debug("Core TenantContext not available - running in standalone mode");
        } catch (Exception e) {
            log.debug("Could not access core TenantContext: {}", e.getMessage());
        }

        if (DevTenantContext.isSet()) {
            String tenantId = DevTenantContext.getTenantId();
            log.debug("Tenant ID from dev context: {}", tenantId);
            return tenantId;
        }

        log.error("No tenant ID available");
        throw new RuntimeException("Failed to get tenant ID from context");
    }




    public static UUID getFacilityIdFromContext() {
        log.debug("getFacilityIdFromContext() called");

        try {
            Class<?> tenantContextClass = Class.forName("coreapplication.service.plugin_manager.TenantContext");
            java.lang.reflect.Method method = tenantContextClass.getMethod("getFacilityIdOrNull");
            UUID facilityId = (UUID) method.invoke(null);

            if (facilityId != null) {
                log.debug("Facility ID from core context: {}", facilityId);
                return facilityId;
            }
        } catch (ClassNotFoundException e) {
            log.debug("Core TenantContext not available");
        } catch (Exception e) {
            log.debug("Could not access core TenantContext for facility: {}", e.getMessage());
        }

        if (DevTenantContext.isSet()) {
            UUID facilityId = DevTenantContext.getFacilityId();
            log.debug("Facility ID from dev context: {}", facilityId);
            return facilityId;
        }

        log.debug("No facility ID in context");
        return null;
    }


    public static UUID getFacilityIdFromContext(boolean required) {
        UUID facilityId = getFacilityIdFromContext();
        if (facilityId == null && required) {
            throw new IllegalStateException("Facility ID is required");
        }
        return facilityId;
    }

        // ============================================================
    // ADMIN ROLE CHECKS (reflection into core SecurityUtils)
    // ============================================================

    public static boolean isCurrentUserSuperAdmin() {
        return invokeSecurityUtilsBoolean("isCurrentUserSuperAdmin");
    }

    public static boolean isCurrentUserTenantAdmin() {
        return invokeSecurityUtilsBoolean("isCurrentUserTenantAdmin");
    }

    public static boolean isCurrentUserFacilityAdmin() {
        return invokeSecurityUtilsBoolean("isCurrentUserFacilityAdmin");
    }


    public static boolean hasCrossFacilityAccess() {
        return isCurrentUserSuperAdmin() || isCurrentUserTenantAdmin();
    }

    private static boolean invokeSecurityUtilsBoolean(String methodName) {
        try {
            Class<?> clazz = Class.forName("coreapplication.security.SecurityUtils");
            java.lang.reflect.Method method = clazz.getMethod(methodName);
            return (boolean) method.invoke(null);
        } catch (ClassNotFoundException e) {
            log.debug("Core SecurityUtils not available - assuming false for {}", methodName);
            return false;
        } catch (Exception e) {
            log.debug("Could not invoke SecurityUtils.{}: {}", methodName, e.getMessage());
            return false;
        }
    }


    public static String resolveFacilityScope() {
        if (hasCrossFacilityAccess()) {
            return null;
        }
        UUID facilityId = getFacilityIdFromContext();
        if (facilityId == null) {
            throw new IllegalStateException(
                    "No facility context for non-admin user. Select a facility before proceeding.");
        }
        return facilityId.toString();
    }

    public static UserInfo getCurrentUser() {
        log.debug("getCurrentUser() called");

        try {
            Class<?> securityContextHolderClass = Class.forName(
                    "org.springframework.security.core.context.SecurityContextHolder");
            java.lang.reflect.Method getContext = securityContextHolderClass.getMethod("getContext");
            Object securityContext = getContext.invoke(null);

            java.lang.reflect.Method getAuthentication = securityContext.getClass().getMethod("getAuthentication");
            Object authentication = getAuthentication.invoke(securityContext);

            if (authentication == null) return null;

            java.lang.reflect.Method isAuthenticated = authentication.getClass().getMethod("isAuthenticated");
            boolean authenticated = (boolean) isAuthenticated.invoke(authentication);
            if (!authenticated) return null;

            java.lang.reflect.Method getPrincipal = authentication.getClass().getMethod("getPrincipal");
            Object principal = getPrincipal.invoke(authentication);

            if (principal == null || "anonymousUser".equals(principal)) return null;

            try {
                Class<?> customUserDetailsClass = Class.forName("coreapplication.security.CustomUserDetails");
                if (customUserDetailsClass.isInstance(principal)) {
                    java.lang.reflect.Method getUser = customUserDetailsClass.getMethod("getUser");
                    Object user = getUser.invoke(principal);
                    Class<?> userClass = user.getClass();

                    String email = (String) userClass.getMethod("getEmail").invoke(user);
                    String fullName = (String) userClass.getMethod("getFullName").invoke(user);
                    String userId = (String) userClass.getMethod("getId").invoke(user);
                    String tenantId = (String) userClass.getMethod("getTenantId").invoke(user);

                    return new UserInfo(userId, email, fullName, tenantId);
                }
            } catch (ClassNotFoundException e) {
                log.debug("CustomUserDetails not available");
            }

            if (principal instanceof String username) {
                return new UserInfo(null, username, username, getTenantIdSafe());
            }

            java.lang.reflect.Method getName = authentication.getClass().getMethod("getName");
            String name = (String) getName.invoke(authentication);
            return new UserInfo(null, name, name, getTenantIdSafe());

        } catch (Exception e) {
            log.error("Could not get current user: {}", e.getMessage(), e);
            return null;
        }
    }

    public static String getCurrentUserEmail() {
        UserInfo user = getCurrentUser();
        return user != null ? user.email() : "system";
    }

    public static String getCurrentUserFullName() {
        UserInfo user = getCurrentUser();
        return user != null ? user.fullName() : null;
    }

    private static String getTenantIdSafe() {
        try {
            return getTenantIdFromContext();
        } catch (Exception e) {
            return null;
        }
    }

    public record UserInfo(
            String userId,
            String email,
            String fullName,
            String tenantId
    ) {}
}