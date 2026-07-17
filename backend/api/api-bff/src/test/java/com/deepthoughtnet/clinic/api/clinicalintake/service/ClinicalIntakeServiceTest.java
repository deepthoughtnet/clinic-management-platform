package com.deepthoughtnet.clinic.api.clinicalintake.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicalintake.db.PatientClinicalIntakeEntity;
import com.deepthoughtnet.clinic.api.clinicalintake.db.PatientClinicalIntakeRepository;
import com.deepthoughtnet.clinic.api.clinicalintake.dto.ClinicalIntakeRequest;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class ClinicalIntakeServiceTest {
    @Test
    void saveAndLatestUseTenantScopedIntake() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID actorAppUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();

        var patient = mock(com.deepthoughtnet.clinic.patient.db.PatientEntity.class);

        AppUserEntity user = mock(AppUserEntity.class);
        when(user.getDisplayName()).thenReturn("Reception Desk");
        when(user.getEmail()).thenReturn("receptionist@clinic.local");

        PatientRepository patientRepository = mock(PatientRepository.class);
        PatientClinicalIntakeRepository intakeRepository = mock(PatientClinicalIntakeRepository.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);

        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(Optional.of(patient));
        when(appUserRepository.findByTenantIdAndId(tenantId, actorAppUserId)).thenReturn(Optional.of(user));
        when(intakeRepository.findFirstByTenantIdAndPatientIdAndAppointmentIdOrderByCreatedAtDesc(tenantId, patientId, appointmentId)).thenReturn(Optional.empty());

        ClinicalIntakeService service = new ClinicalIntakeService(patientRepository, intakeRepository, appUserRepository);
        ClinicalIntakeRequest request = new ClinicalIntakeRequest(
                appointmentId,
                null,
                "Fever and cough",
                170.0,
                72.0,
                140,
                88,
                102,
                38.1,
                TemperatureUnit.CELSIUS,
                97,
                20,
                110.0,
                7,
                "Needs quick review",
                true
        );

        when(intakeRepository.save(ArgumentMatchers.any(PatientClinicalIntakeEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var saved = service.save(tenantId, patientId, request, actorAppUserId);
        assertThat(saved.status()).isEqualTo("INTAKE_COMPLETE");
        assertThat(saved.chiefComplaint()).isEqualTo("Fever and cough");
        assertThat(saved.bmi()).isNotNull();
        assertThat(saved.recordedByName()).isEqualTo("Reception Desk");

        when(intakeRepository.findFirstByTenantIdAndPatientIdAndAppointmentIdOrderByCreatedAtDesc(tenantId, patientId, appointmentId))
                .thenReturn(Optional.of(PatientClinicalIntakeEntity.create(
                        UUID.randomUUID(),
                        tenantId,
                        patientId,
                        appointmentId,
                        null,
                        "Fever and cough",
                        170.0,
                        72.0,
                        24.9,
                        140,
                        88,
                        102,
                        38.1,
                        TemperatureUnit.CELSIUS,
                        97,
                        20,
                        110.0,
                        7,
                        "Needs quick review",
                        actorAppUserId,
                        "Reception Desk",
                        true,
                        actorAppUserId,
                        actorAppUserId
                )));

        assertThat(service.latest(tenantId, patientId, appointmentId, null)).isPresent();
        assertThat(service.latest(tenantId, patientId, appointmentId, null).orElseThrow().status()).isEqualTo("INTAKE_COMPLETE");
    }

    @Test
    void latestFallsBackToConsultationLinkedIntakeWhenAppointmentMismatch() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        PatientClinicalIntakeRepository intakeRepository = mock(PatientClinicalIntakeRepository.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        ClinicalIntakeService service = new ClinicalIntakeService(patientRepository, intakeRepository, appUserRepository);

        when(intakeRepository.findFirstByTenantIdAndPatientIdAndAppointmentIdOrderByCreatedAtDesc(tenantId, patientId, appointmentId)).thenReturn(Optional.empty());
        when(intakeRepository.findFirstByTenantIdAndPatientIdAndConsultationIdOrderByCreatedAtDesc(tenantId, patientId, consultationId)).thenReturn(Optional.of(PatientClinicalIntakeEntity.create(
                UUID.randomUUID(),
                tenantId,
                patientId,
                null,
                consultationId,
                "Follow-up",
                175.0,
                82.0,
                26.8,
                138,
                86,
                96,
                38.4,
                TemperatureUnit.CELSIUS,
                97,
                18,
                186.0,
                4,
                "Linked to consultation",
                UUID.randomUUID(),
                "Doctor",
                true,
                UUID.randomUUID(),
                UUID.randomUUID()
        )));

        assertThat(service.latest(tenantId, patientId, appointmentId, consultationId)).isPresent();
        assertThat(service.latest(tenantId, patientId, appointmentId, consultationId).orElseThrow().consultationId()).isEqualTo(consultationId);
    }
}
