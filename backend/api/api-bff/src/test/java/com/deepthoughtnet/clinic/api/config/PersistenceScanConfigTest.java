package com.deepthoughtnet.clinic.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.reliability.db.IdempotencyKeyEntity;
import com.deepthoughtnet.clinic.api.reliability.db.IdempotencyKeyRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobRepository;
import com.deepthoughtnet.clinic.identity.db.TenantPlanEntity;
import com.deepthoughtnet.clinic.identity.db.TenantPlanRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import(PersistenceScanConfig.class)
class PersistenceScanConfigTest {
    @Autowired
    private ClinicalAiJobRepository clinicalAiJobRepository;

    @Autowired
    private TenantPlanRepository tenantPlanRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void clinicalAiJobRepositoryIsRegistered() {
        assertThat(clinicalAiJobRepository).isNotNull();
    }

    @Test
    void idempotencyKeysPersistWithPortableColumnNames() {
        UUID tenantId = UUID.randomUUID();
        idempotencyKeyRepository.saveAndFlush(
                IdempotencyKeyEntity.create(tenantId, "request-123", "hash-123", "{\"status\":\"ok\"}")
        );
        entityManager.clear();

        IdempotencyKeyEntity reloaded = idempotencyKeyRepository.findByTenantIdAndIdempotencyKey(tenantId, "request-123")
                .orElseThrow();

        assertThat(reloaded.getIdempotencyKey()).isEqualTo("request-123");
        assertThat(reloaded.getRequestHash()).isEqualTo("hash-123");
        assertThat(reloaded.getResponseJson()).contains("\"status\":\"ok\"");
    }

    @Test
    void tenantPlanFeaturesRoundTripAsJson() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("clinicalAutomation", true);
        features.put("limits", Map.of("patients", 200));

        tenantPlanRepository.saveAndFlush(TenantPlanEntity.create("TRIAL", "Trial", features));
        entityManager.clear();

        TenantPlanEntity reloaded = tenantPlanRepository.findById("TRIAL").orElseThrow();

        assertThat(reloaded.getName()).isEqualTo("Trial");
        assertThat(reloaded.getFeatures()).containsEntry("clinicalAutomation", true);
        assertThat(reloaded.getFeatures()).containsKey("limits");
    }
}
