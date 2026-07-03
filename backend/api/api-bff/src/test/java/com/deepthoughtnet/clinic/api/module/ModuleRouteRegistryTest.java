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
    void mapsLaboratoryApiPathToLaboratoryModule() {
        ModuleRouteRegistry registry = new ModuleRouteRegistry();

        String moduleKey = registry.moduleForPath("/api/lab/orders");

        assertThat(moduleKey).isEqualTo(SaasModuleCode.LABORATORY.name());
    }

    @Test
    void mapsPatientsApiPathToPatientsModule() {
        ModuleRouteRegistry registry = new ModuleRouteRegistry();

        String moduleKey = registry.moduleForPath("/api/patients");

        assertThat(moduleKey).isEqualTo(SaasModuleCode.PATIENTS.name());
    }

    @Test
    void mapsReportsApiPathToReportsModule() {
        ModuleRouteRegistry registry = new ModuleRouteRegistry();

        String moduleKey = registry.moduleForPath("/api/reports/summary");

        assertThat(moduleKey).isEqualTo(SaasModuleCode.REPORTS.name());
    }

    @Test
    void returnsNullForUnknownPath() {
        ModuleRouteRegistry registry = new ModuleRouteRegistry();

        String moduleKey = registry.moduleForPath("/api/unknown");

        assertThat(moduleKey).isNull();
    }
}
