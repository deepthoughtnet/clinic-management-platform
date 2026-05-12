package com.deepthoughtnet.clinic.api.module;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.platform.core.module.SaasModuleCode;
import org.junit.jupiter.api.Test;

class ModuleRouteRegistryTest {

    @Test
    void mapsCarePilotApiPathToCarePilotModule() {
        ModuleRouteRegistry registry = new ModuleRouteRegistry();

        String moduleKey = registry.moduleForPath("/api/carepilot/campaigns");

        assertThat(moduleKey).isEqualTo(SaasModuleCode.CAREPILOT.name());
    }

    @Test
    void returnsNullForUnknownPath() {
        ModuleRouteRegistry registry = new ModuleRouteRegistry();

        String moduleKey = registry.moduleForPath("/api/unknown");

        assertThat(moduleKey).isNull();
    }
}
