package com.deepthoughtnet.clinic.api.medicationsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.config.PersistenceScanConfig;
import com.deepthoughtnet.clinic.api.medicationsafety.db.PrescriptionSafetyReviewEntity;
import com.deepthoughtnet.clinic.api.medicationsafety.db.PrescriptionSafetyReviewRepository;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        PersistenceScanConfig.class,
        MedicationSafetyEngine.class,
        MedicationSafetySnapshotHasher.class,
        MedicationSafetyReviewService.class,
        MedicationSafetyController.class,
        MedicationSafetySpringContextTest.TestBeans.class
})
class MedicationSafetySpringContextTest extends com.deepthoughtnet.clinic.api.support.AbstractPostgresDataJpaTest {
    @MockBean MedicationSafetyService medicationSafetyService;
    @MockBean AuditEventPublisher auditEventPublisher;
    @MockBean com.deepthoughtnet.clinic.api.security.PermissionChecker permissionChecker;
    @MockBean DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    @Autowired
    private PrescriptionSafetyReviewRepository repository;

    @Autowired
    private MedicationSafetyReviewService medicationSafetyReviewService;

    @Autowired
    private MedicationSafetyController medicationSafetyController;

    @Test
    void medicationSafetyBeansLoadAndRepositoryPersistsEntity() {
        assertThat(repository).isNotNull();
        assertThat(medicationSafetyReviewService).isNotNull();
        assertThat(medicationSafetyController).isNotNull();

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        PrescriptionSafetyReviewEntity entity = PrescriptionSafetyReviewEntity.create(
                tenantId,
                patientId,
                consultationId,
                prescriptionId,
                1,
                "prescription-hash",
                "patient-context-hash",
                "med-safety-v1",
                "evaluation-1",
                MedicationSafetySeverity.WARNING,
                "{\"findings\":[]}",
                MedicationSafetyReviewDecisionStatus.NOT_REVIEWED.name(),
                null,
                null,
                null,
                null,
                null
        );
        repository.saveAndFlush(entity);

        PrescriptionSafetyReviewEntity reloaded = repository.findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(tenantId, prescriptionId)
                .orElseThrow();
        assertThat(reloaded.getTenantId()).isEqualTo(tenantId);
        assertThat(reloaded.getPrescriptionId()).isEqualTo(prescriptionId);
        assertThat(reloaded.getDecisionStatus()).isEqualTo(MedicationSafetyReviewDecisionStatus.NOT_REVIEWED.name());
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
