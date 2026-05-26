package com.lamiplus_common_api.common;


import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(name = "plugin.standalone.enabled", havingValue = "true")
public class DevTenantInitializer {

    @Value("${plugin.standalone.tenant-id:demo-hospital}")
    private String devTenantId;

    @Value("${plugin.standalone.facility-id:c6b48c14-cc07-4272-9ad6-61111bbf81b7}")
    private UUID devFacilityId;

    @PostConstruct
    public void initialize() {
        log.info("════════════════════════════════════════");
        log.info("DevTenantInitializer @PostConstruct CALLED");
        log.info("Dev Tenant ID from config: {}", devTenantId);
        log.info("Dev Facility ID from config: {}", devFacilityId);


        DevTenantContext.setTenant(devTenantId, devFacilityId);

        log.info("DevTenantContext.isSet(): {}", DevTenantContext.isSet());
        log.info("DevTenantContext.getTenantId(): {}", DevTenantContext.getTenantId());
        log.info("DevTenantContext.getFacilityId(): {}", DevTenantContext.getFacilityId());
        log.info("════════════════════════════════════════");
    }
}