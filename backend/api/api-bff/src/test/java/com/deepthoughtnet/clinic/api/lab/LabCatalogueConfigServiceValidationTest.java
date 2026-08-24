package com.deepthoughtnet.clinic.api.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.lab.db.LabCategorySettingRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterRepository;
import com.deepthoughtnet.clinic.api.lab.dto.LabCategoryConfigDtos.LabCategoryConfigUpdateRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabTestCatalogueConfigDtos.LabTestCatalogueConfigUpdateRequest;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabCatalogueConfigServiceValidationTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private LabCategorySettingRepository labCategorySettingRepository;
    @Mock private LabTestMasterRepository labTestMasterRepository;

    private LabCatalogueConfigService service;

    @BeforeEach
    void setUp() {
        service = new LabCatalogueConfigService(labCategorySettingRepository, labTestMasterRepository);
    }

    @Test
    void rejectsBlankCategoryDisplayName() {
        assertThatThrownBy(() -> service.updateCategory(TENANT_ID, "HEMATOLOGY", new LabCategoryConfigUpdateRequest("   ", true, 1), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName");
    }

    @Test
    void rejectsInvalidTenantOverridesAndDisplayOrder() {
        LabTestMasterEntity entity = LabTestMasterEntity.create(TENANT_ID, "CBC", "Complete Blood Count");
        entity.update("CBC", "Complete Blood Count", "HEMATOLOGY", null, "Blood", null, null, "24", BigDecimal.valueOf(100), true);
        when(labTestMasterRepository.findByTenantIdAndId(TENANT_ID, TEST_ID)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateTest(TENANT_ID, TEST_ID, new LabTestCatalogueConfigUpdateRequest(true, true, new BigDecimal("-1"), "24 hrs", -1), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero or greater");

        assertThatThrownBy(() -> service.updateTest(TENANT_ID, TEST_ID, new LabTestCatalogueConfigUpdateRequest(true, true, BigDecimal.valueOf(100), "24 hrs", 1), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantTatOverride");

        assertThatThrownBy(() -> service.updateTest(TENANT_ID, TEST_ID, new LabTestCatalogueConfigUpdateRequest(true, true, BigDecimal.valueOf(100), "24", -1), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero or greater");
    }

    @Test
    void rejectsNegativeCategoryDisplayOrderWithCorrectMessage() {
        assertThatThrownBy(() -> service.updateCategory(TENANT_ID, "HEMATOLOGY", new LabCategoryConfigUpdateRequest("Hematology", true, -1), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero or greater");
    }

    @Test
    void rejectsNegativeTenantDisplayOrderWithCorrectMessage() {
        LabTestMasterEntity entity = LabTestMasterEntity.create(TENANT_ID, "CBC", "Complete Blood Count");
        entity.update("CBC", "Complete Blood Count", "HEMATOLOGY", null, "Blood", null, null, "24", BigDecimal.valueOf(100), true);
        when(labTestMasterRepository.findByTenantIdAndId(TENANT_ID, TEST_ID)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateTest(TENANT_ID, TEST_ID, new LabTestCatalogueConfigUpdateRequest(true, true, BigDecimal.valueOf(100), "24", -1), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zero or greater");
    }

    @Test
    void clearsOverridesBySavingNulls() {
        LabTestMasterEntity entity = LabTestMasterEntity.create(TENANT_ID, "CBC", "Complete Blood Count");
        entity.update("CBC", "Complete Blood Count", "HEMATOLOGY", null, "Blood", null, null, "24", BigDecimal.valueOf(100), true);
        when(labTestMasterRepository.findByTenantIdAndId(TENANT_ID, TEST_ID)).thenReturn(Optional.of(entity));
        when(labTestMasterRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateTest(TENANT_ID, TEST_ID, new LabTestCatalogueConfigUpdateRequest(false, false, null, null, null), UUID.randomUUID());

        assertThat(response.tenantPriceOverride()).isNull();
        assertThat(response.tenantTatOverride()).isNull();
        assertThat(response.displayOrder()).isNull();
    }
}
