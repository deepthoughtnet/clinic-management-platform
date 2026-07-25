package com.deepthoughtnet.clinic.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionService;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialSubscriptionEventRepository;
import com.deepthoughtnet.clinic.commercial.subscription.db.CommercialTenantSubscriptionRepository;
import com.deepthoughtnet.clinic.api.reliability.db.IdempotencyKeyEntity;
import com.deepthoughtnet.clinic.api.reliability.db.IdempotencyKeyRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobRepository;
import com.deepthoughtnet.clinic.identity.db.TenantOnboardingEntity;
import com.deepthoughtnet.clinic.identity.db.TenantOnboardingRepository;
import com.deepthoughtnet.clinic.identity.db.TenantPlanEntity;
import com.deepthoughtnet.clinic.identity.db.TenantPlanRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePrescriptionRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleRepository;
import com.deepthoughtnet.clinic.platform.modulith.events.db.ModuleBusinessEventRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        PersistenceScanConfig.class,
        CommercialSubscriptionService.class,
        PersistenceScanConfigTest.TestBeans.class
})
class PersistenceScanConfigTest {
    @Autowired
    private ClinicalAiJobRepository clinicalAiJobRepository;

    @Autowired
    private TenantPlanRepository tenantPlanRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private PharmacySaleRepository pharmacySaleRepository;

    @Autowired
    private PharmacySalePrescriptionRepository pharmacySalePrescriptionRepository;

    @Autowired
    private TenantOnboardingRepository tenantOnboardingRepository;

    @Autowired
    private ModuleBusinessEventRepository moduleBusinessEventRepository;

    @Autowired
    private CommercialTenantSubscriptionRepository commercialTenantSubscriptionRepository;

    @Autowired
    private CommercialSubscriptionEventRepository commercialSubscriptionEventRepository;

    @Autowired
    private CommercialSubscriptionService commercialSubscriptionService;

    @Autowired
    private TestEntityManager entityManager;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @Test
    void clinicalAiJobRepositoryIsRegistered() {
        assertThat(clinicalAiJobRepository).isNotNull();
    }

    @Test
    void pharmacySaleRepositoryIsRegistered() {
        assertThat(pharmacySaleRepository).isNotNull();
    }

    @Test
    void pharmacySalePrescriptionRepositoryIsRegistered() {
        assertThat(pharmacySalePrescriptionRepository).isNotNull();
    }

    @Test
    void tenantOnboardingRepositoryIsRegistered() {
        assertThat(tenantOnboardingRepository).isNotNull();
    }

    @Test
    void moduleBusinessEventRepositoryIsRegisteredThroughPublicPlatformConfiguration() {
        assertThat(moduleBusinessEventRepository).isNotNull();
    }

    @Test
    void commercialSubscriptionRepositoriesAndServiceAreRegistered() {
        assertThat(commercialTenantSubscriptionRepository).isNotNull();
        assertThat(commercialSubscriptionEventRepository).isNotNull();
        assertThat(commercialSubscriptionService).isNotNull();
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

    @Test
    void tenantOnboardingRoundTripWorks() {
        UUID tenantId = UUID.randomUUID();
        tenantOnboardingRepository.saveAndFlush(TenantOnboardingEntity.create(tenantId, false));
        entityManager.clear();

        TenantOnboardingEntity reloaded = tenantOnboardingRepository.findByTenantId(tenantId).orElseThrow();
        assertThat(reloaded.isCompleted()).isFalse();
        assertThat(reloaded.isSkipped()).isFalse();
        assertThat(reloaded.getTenantId()).isEqualTo(tenantId);
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
