package com.lamiplus_common_api.common;

import java.util.UUID;

public class TenantContextData {
    private String tenantId;
    private UUID facilityId;

    public TenantContextData(String tenantId, UUID facilityId) {
        this.tenantId = tenantId;
        this.facilityId = facilityId;
    }

    public String getTenantId() { return tenantId; }
    public UUID getFacilityId() { return facilityId; }
}
