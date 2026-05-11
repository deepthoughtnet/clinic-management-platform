package com.deepthoughtnet.clinic.api.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = {
        "com.deepthoughtnet.clinic.platform.audit.db",
        "com.deepthoughtnet.clinic.api.clinicaldocument.db",
        "com.deepthoughtnet.clinic.api.clinicaldocument.ai.db",
        "com.deepthoughtnet.clinic.api.prescriptiontemplate.db",
        "com.deepthoughtnet.clinic.api.inventory.db",
        "com.deepthoughtnet.clinic.api.reliability.db",
        "com.deepthoughtnet.clinic.notification.db",
        "com.deepthoughtnet.clinic.identity.db",
        "com.deepthoughtnet.clinic.ai.orchestration.db",
        "com.deepthoughtnet.clinic.clinic.db",
        "com.deepthoughtnet.clinic.patient.db",
        "com.deepthoughtnet.clinic.appointment.db",
        "com.deepthoughtnet.clinic.consultation.db",
        "com.deepthoughtnet.clinic.prescription.db",
        "com.deepthoughtnet.clinic.billing.db",
        "com.deepthoughtnet.clinic.vaccination.db",
        "com.deepthoughtnet.clinic.inventory.db"
})
@EnableJpaRepositories(basePackages = {
        "com.deepthoughtnet.clinic.platform.audit.db",
        "com.deepthoughtnet.clinic.api.clinicaldocument.db",
        "com.deepthoughtnet.clinic.api.clinicaldocument.ai.db",
        "com.deepthoughtnet.clinic.api.prescriptiontemplate.db",
        "com.deepthoughtnet.clinic.api.inventory.db",
        "com.deepthoughtnet.clinic.api.reliability.db",
        "com.deepthoughtnet.clinic.notification.db",
        "com.deepthoughtnet.clinic.identity.db",
        "com.deepthoughtnet.clinic.ai.orchestration.db",
        "com.deepthoughtnet.clinic.clinic.db",
        "com.deepthoughtnet.clinic.patient.db",
        "com.deepthoughtnet.clinic.appointment.db",
        "com.deepthoughtnet.clinic.consultation.db",
        "com.deepthoughtnet.clinic.prescription.db",
        "com.deepthoughtnet.clinic.billing.db",
        "com.deepthoughtnet.clinic.vaccination.db",
        "com.deepthoughtnet.clinic.inventory.db"
})
public class PersistenceScanConfig {
}
