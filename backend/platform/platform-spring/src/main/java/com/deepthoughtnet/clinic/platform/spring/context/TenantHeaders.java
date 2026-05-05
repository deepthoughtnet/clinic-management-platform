package com.deepthoughtnet.clinic.platform.spring.context;

public final class TenantHeaders {
    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String TENANT_CLAIM = "tenant_id"; // must be added in Keycloak token mapper
    private TenantHeaders() {}
}
