package com.deepthoughtnet.clinic.api.clinicalmemory.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.config.PersistenceScanConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import(PersistenceScanConfig.class)
class PatientLongitudinalConceptRepositoryTest {

    @Autowired
    private PatientLongitudinalConceptRepository repository;

    @Test
    void repositoryBeanIsCreated() {
        assertThat(repository).isNotNull();
    }

    @Test
    void repositoryInterfaceCanPersistEntity() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        PatientLongitudinalConceptEntity entity = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                UUID.randomUUID(),
                "EXTERNAL_LAB_REPORT",
                "Diabetes Follow-up Laboratory Report",
                java.time.LocalDate.of(2026, 1, 8),
                "LAB_RESULT",
                "hba1c",
                "HbA1c",
                "8.4",
                "%",
                "HbA1c 8.4",
                "Clinical extraction",
                "PENDING_REVIEW",
                new java.math.BigDecimal("0.96"),
                java.time.OffsetDateTime.now()
        );

        repository.saveAndFlush(entity);

        assertThat(repository.findByTenantIdAndPatientIdOrderByObservedAtDescCreatedAtDesc(tenantId, patientId))
                .hasSize(1);
    }
}
