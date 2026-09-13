package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatientPortalCareAiEntityRegistryTest {
    @Test
    void specialityEntityIncludesCanonicalAliases() {
        PatientPortalCareAiEntityRegistry registry = new PatientPortalCareAiEntityRegistry();

        assertThat(registry.aliasesFor(PatientPortalCareAiEntityType.SPECIALITY))
                .contains(
                        "speciality",
                        "specialty",
                        "department",
                        "general medicine",
                        "general physician",
                        "physician",
                        "gp",
                        "family physician",
                        "general practitioner",
                        "जनरल फिजिशियन",
                        "जनरल मेडिसिन"
                );
        assertThat(registry.examplesFor(PatientPortalCareAiEntityType.SPECIALITY))
                .contains("General Medicine", "General Physician", "physician", "GP");
    }

    @Test
    void doctorClinicLocationAndServiceEntitiesExposeCanonicalAliases() {
        PatientPortalCareAiEntityRegistry registry = new PatientPortalCareAiEntityRegistry();

        assertThat(registry.aliasesFor(PatientPortalCareAiEntityType.DOCTOR))
                .contains("dr", "doctor", "doc");
        assertThat(registry.examplesFor(PatientPortalCareAiEntityType.DOCTOR))
                .contains("Doc Akshu Kumar", "UAT doctor");

        assertThat(registry.aliasesFor(PatientPortalCareAiEntityType.CLINIC))
                .contains("clinic", "hospital", "centre", "center", "branch");
        assertThat(registry.examplesFor(PatientPortalCareAiEntityType.CLINIC))
                .contains("Jeevanam Automation Lab", "Demo Clinic");

        assertThat(registry.aliasesFor(PatientPortalCareAiEntityType.LOCATION))
                .contains("location", "area", "city", "locality", "near");
        assertThat(registry.examplesFor(PatientPortalCareAiEntityType.LOCATION))
                .contains("Pune", "Baner", "Kharadi");

        assertThat(registry.aliasesFor(PatientPortalCareAiEntityType.SERVICE))
                .contains("service", "services", "consultation", "health check", "teleconsultation", "follow up consultation");
        assertThat(registry.examplesFor(PatientPortalCareAiEntityType.SERVICE))
                .contains("consultation", "health check", "teleconsultation", "follow-up consultation");
    }
}
