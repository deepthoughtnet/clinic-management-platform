package com.deepthoughtnet.clinic.platform.core.config;

import java.util.LinkedHashSet;
import java.util.Set;

public class CommercialRuntimeProperties {
    private boolean enabled = false;
    private boolean shadowCompareEnabled = false;
    private boolean fallbackToLegacyEnabled = false;
    private Set<String> tenantAllowlist = new LinkedHashSet<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isShadowCompareEnabled() {
        return shadowCompareEnabled;
    }

    public void setShadowCompareEnabled(boolean shadowCompareEnabled) {
        this.shadowCompareEnabled = shadowCompareEnabled;
    }

    public boolean isFallbackToLegacyEnabled() {
        return fallbackToLegacyEnabled;
    }

    public void setFallbackToLegacyEnabled(boolean fallbackToLegacyEnabled) {
        this.fallbackToLegacyEnabled = fallbackToLegacyEnabled;
    }

    public Set<String> getTenantAllowlist() {
        return tenantAllowlist;
    }

    public void setTenantAllowlist(Set<String> tenantAllowlist) {
        this.tenantAllowlist = tenantAllowlist == null ? new LinkedHashSet<>() : new LinkedHashSet<>(tenantAllowlist);
    }
}
