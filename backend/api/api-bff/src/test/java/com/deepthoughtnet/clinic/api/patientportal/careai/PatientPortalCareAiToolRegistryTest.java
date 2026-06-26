package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatientPortalCareAiToolRegistryTest {
    private final PatientPortalCareAiToolRegistry registry = new PatientPortalCareAiToolRegistry();

    @Test
    void findDoctorRequiresDoctorOrSpecialityMetadata() {
        PatientPortalCareAiToolDefinition definition = registry.definitionFor(PatientPortalCareAiToolType.FIND_DOCTOR);
        assertThat(definition.requiredEntities()).contains(PatientPortalCareAiEntityType.DOCTOR, PatientPortalCareAiEntityType.SPECIALITY);
    }

    @Test
    void findSlotsRequiresDoctorAndDate() {
        PatientPortalCareAiToolDefinition definition = registry.definitionFor(PatientPortalCareAiToolType.FIND_SLOTS);
        assertThat(definition.requiredEntities()).contains(PatientPortalCareAiEntityType.DOCTOR, PatientPortalCareAiEntityType.DATE);
    }

    @Test
    void bookAppointmentRequiresDoctorDateAndTimeSlotWithConfirmation() {
        PatientPortalCareAiToolDefinition definition = registry.definitionFor(PatientPortalCareAiToolType.BOOK_APPOINTMENT);
        assertThat(definition.requiredEntities()).contains(
                PatientPortalCareAiEntityType.DOCTOR,
                PatientPortalCareAiEntityType.DATE,
                PatientPortalCareAiEntityType.TIME_SLOT
        );
        assertThat(definition.confirmationRequired()).isTrue();
    }

    @Test
    void findAppointmentsUsesPatientIdentityMetadata() {
        PatientPortalCareAiToolDefinition definition = registry.definitionFor(PatientPortalCareAiToolType.FIND_APPOINTMENTS);
        assertThat(definition.requiredEntities()).isEmpty();
        assertThat(definition.description()).contains("authenticated patient");
    }

    @Test
    void cancelAppointmentRequiresAppointmentAndConfirmation() {
        PatientPortalCareAiToolDefinition definition = registry.definitionFor(PatientPortalCareAiToolType.CANCEL_APPOINTMENT);
        assertThat(definition.requiredEntities()).contains(PatientPortalCareAiEntityType.APPOINTMENT);
        assertThat(definition.confirmationRequired()).isTrue();
    }

    @Test
    void rescheduleAppointmentRequiresAppointmentDateAndSlotWithConfirmation() {
        PatientPortalCareAiToolDefinition definition = registry.definitionFor(PatientPortalCareAiToolType.RESCHEDULE_APPOINTMENT);
        assertThat(definition.requiredEntities()).contains(
                PatientPortalCareAiEntityType.APPOINTMENT,
                PatientPortalCareAiEntityType.DATE,
                PatientPortalCareAiEntityType.TIME_SLOT
        );
        assertThat(definition.confirmationRequired()).isTrue();
    }
}
