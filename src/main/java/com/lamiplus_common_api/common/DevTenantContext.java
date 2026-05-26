package com.lamiplus_common_api.common;



import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class DevTenantContext {

    private static final ThreadLocal<TenantContextData> currentTenant = new ThreadLocal<>();

    public static void setTenant(String tenantId, UUID facilityId) {
        currentTenant.set(new TenantContextData(tenantId, facilityId));
    }

    public static String getTenantId() {
        return currentTenant.get().getTenantId();
    }

    public static UUID getFacilityId() {
        return currentTenant.get().getFacilityId();
    }

    public static void clear() {
        currentTenant.remove();
    }



    public static boolean isSet() {
        return currentTenant.get() != null;
    }
}
