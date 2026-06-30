package com.deepthoughtnet.clinic.api.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantModuleServiceTest {

    @Test
    void normalizesPharmacyPosModuleCode() {
        TenantModuleService service = new TenantModuleService((JdbcTemplate) null);

        assertThat(TenantModuleService.SUPPORTED_MODULES).contains("PHARMACY_POS");
        assertThat(service.normalizeModule(" pharmacy_pos ")).isEqualTo("PHARMACY_POS");
    }
}
