package com.deepthoughtnet.clinic.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import(PersistenceScanConfig.class)
class PersistenceScanConfigTest {
    @Autowired
    private ClinicalAiJobRepository clinicalAiJobRepository;

    @Test
    void clinicalAiJobRepositoryIsRegistered() {
        assertThat(clinicalAiJobRepository).isNotNull();
    }
}
