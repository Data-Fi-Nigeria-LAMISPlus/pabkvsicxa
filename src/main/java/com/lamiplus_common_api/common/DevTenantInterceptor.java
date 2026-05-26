package com.lamiplus_common_api.common;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Slf4j
@Component
@Profile("dev")

@RequiredArgsConstructor
public class DevTenantInterceptor implements HandlerInterceptor {

    @Value("${plugin.standalone.tenant-id:demo-hospital}")
    private String devTenantId;

    @Value("${plugin.standalone.facility-id:c6b48c14-cc07-4272-9ad6-61111bbf81b7}")
    private UUID devFacilityId;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        log.debug("DevTenantInterceptor: Setting tenant to {}", devTenantId);
        log.debug("DevFacilityInterceptor: Setting tenant to {}", devFacilityId);

        DevTenantContext.setTenant(devTenantId, devFacilityId);
        log.debug("DevTenantContext.getTenantId(): {}", DevTenantContext.getTenantId());
        log.debug("DevTenantContext.getFacilityId(): {}", DevTenantContext.getFacilityId());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        log.debug("DevTenantInterceptor: Clearing tenant context");
        DevTenantContext.clear();
    }
}