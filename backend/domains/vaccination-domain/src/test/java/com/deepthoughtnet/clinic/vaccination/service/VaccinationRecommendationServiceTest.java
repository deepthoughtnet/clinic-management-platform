package com.deepthoughtnet.clinic.vaccination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationEntity;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationRepository;
import com.deepthoughtnet.clinic.vaccination.db.VaccineMasterEntity;
import com.deepthoughtnet.clinic.vaccination.db.VaccineMasterRepository;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccinationRecommendationSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VaccinationRecommendationServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PATIENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private VaccineMasterRepository vaccineMasterRepository;
    private PatientVaccinationRepository patientVaccinationRepository;
    private PatientRepository patientRepository;
    private VaccinationRecommendationService service;

    @BeforeEach
    void setUp() {
        vaccineMasterRepository = mock(VaccineMasterRepository.class);
        patientVaccinationRepository = mock(PatientVaccinationRepository.class);
        patientRepository = mock(PatientRepository.class);
        service = new VaccinationRecommendationService(vaccineMasterRepository, patientVaccinationRepository, patientRepository);
    }

    @Test
    void adultDoesNotSeeRoutineChildhoodVaccinesAsOverdue() {
        PatientEntity patient = mockPatient(LocalDate.now().minusYears(44), 44);
        VaccineMasterEntity childhood = vaccine("BCG", "BCG", 0, 0, 365, "STANDARD_CHILDHOOD", "NONE", null, "NEWBORN", false, null, null);
        VaccineMasterEntity adult = vaccine("Influenza", "FLU", 0, 0, null, "ADULT_ROUTINE", "NONE", null, "ADULT", false, null, null);

        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(TENANT_ID)).thenReturn(List.of(childhood, adult));
        when(patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(TENANT_ID, PATIENT_ID)).thenReturn(List.of());

        VaccinationRecommendationSummary summary = service.recommend(TENANT_ID, PATIENT_ID, null);

        assertThat(summary.overdue()).extracting("vaccineName").contains("Influenza");
        assertThat(summary.overdue()).extracting("vaccineName").doesNotContain("BCG");
        assertThat(summary.notApplicable()).extracting("vaccineName").contains("BCG");
    }

    @Test
    void childGetsAgeAppropriateDueVaccinesAndCatchUpWhenAllowed() {
        PatientEntity patient = mockPatient(LocalDate.now().minusDays(70), null);
        VaccineMasterEntity due = vaccine("Pentavalent", "PENTA", 42, 70, 84, "STANDARD_CHILDHOOD", "NONE", null, "INFANT", false, null, null);
        VaccineMasterEntity catchUp = vaccine("MMR Catchup", "MMR", 42, 42, 3650, "CHILDHOOD_CATCHUP", "ALLOWED_UNTIL_AGE", 3650, "CHILD", false, null, null);

        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(TENANT_ID)).thenReturn(List.of(due, catchUp));
        when(patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(TENANT_ID, PATIENT_ID)).thenReturn(List.of());

        VaccinationRecommendationSummary summary = service.recommend(TENANT_ID, PATIENT_ID, null);

        assertThat(summary.recommendedToday()).extracting("vaccineName").contains("Pentavalent");
        assertThat(summary.overdue()).extracting("vaccineName").contains("MMR Catchup");
    }

    @Test
    void childCatchUpIsSuppressedWhenPolicyDoesNotAllowIt() {
        PatientEntity patient = mockPatient(LocalDate.now().minusYears(10), 10);
        VaccineMasterEntity catchUp = vaccine("Polio Catchup", "OPV", 42, 42, 365, "CHILDHOOD_CATCHUP", "NONE", null, "CHILD", false, null, null);

        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(TENANT_ID)).thenReturn(List.of(catchUp));
        when(patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(TENANT_ID, PATIENT_ID)).thenReturn(List.of());

        VaccinationRecommendationSummary summary = service.recommend(TENANT_ID, PATIENT_ID, null);

        assertThat(summary.notApplicable()).extracting("vaccineName").contains("Polio Catchup");
        assertThat(summary.overdue()).isEmpty();
    }

    @Test
    void recurringVaccineIsRecommendedAgainAfterRecurrenceInterval() {
        PatientEntity patient = mockPatient(LocalDate.now().minusYears(44), 44);
        VaccineMasterEntity recurring = vaccine("Influenza", "FLU", 0, 0, null, "RECURRING", "NONE", null, "ADULT", true, 365, null);
        PatientVaccinationEntity history = vaccination(recurring, LocalDate.now().minusDays(400), 1);

        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(TENANT_ID)).thenReturn(List.of(recurring));
        when(patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(TENANT_ID, PATIENT_ID)).thenReturn(List.of(history));

        VaccinationRecommendationSummary summary = service.recommend(TENANT_ID, PATIENT_ID, null);

        assertThat(summary.overdue()).extracting("vaccineName").contains("Influenza");
    }

    @Test
    void boosterVaccineUsesBoosterGapDays() {
        PatientEntity patient = mockPatient(LocalDate.now().minusYears(44), 44);
        VaccineMasterEntity booster = vaccine("Td Booster", "TD", 0, 0, null, "ADULT_ROUTINE", "NONE", null, "ADULT", false, null, 180);
        PatientVaccinationEntity history = vaccination(booster, LocalDate.now().minusDays(200), 1);

        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(TENANT_ID)).thenReturn(List.of(booster));
        when(patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(TENANT_ID, PATIENT_ID)).thenReturn(List.of(history));

        VaccinationRecommendationSummary summary = service.recommend(TENANT_ID, PATIENT_ID, null);

        assertThat(summary.overdue()).extracting("vaccineName").contains("Td Booster");
    }

    @Test
    void optionalRiskBasedVaccinesRenderSeparately() {
        PatientEntity patient = mockPatient(LocalDate.now().minusYears(44), 44);
        VaccineMasterEntity travel = vaccine("Typhoid", "TYPHOID", 0, 0, null, "TRAVEL", "NONE", null, "ADULT", false, null, null);

        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(TENANT_ID)).thenReturn(List.of(travel));
        when(patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(TENANT_ID, PATIENT_ID)).thenReturn(List.of());

        VaccinationRecommendationSummary summary = service.recommend(TENANT_ID, PATIENT_ID, null);

        assertThat(summary.optionalRiskBased()).extracting("vaccineName").contains("Typhoid");
    }

    @Test
    void rejectsInvalidPolicyValues() {
        PatientEntity patient = mockPatient(LocalDate.now().minusYears(44), 44);
        VaccineMasterEntity invalid = vaccine("Broken", "BROKEN", 0, 0, null, "INVALID", "NONE", null, "ADULT", false, null, null);

        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(TENANT_ID)).thenReturn(List.of(invalid));
        when(patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(TENANT_ID, PATIENT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.recommend(TENANT_ID, PATIENT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recommendationPolicy");
    }

    @Test
    void rejectsInvalidScheduleType() {
        PatientEntity patient = mockPatient(LocalDate.now().minusYears(44), 44);
        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        when(vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(TENANT_ID)).thenReturn(List.of());
        when(patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(TENANT_ID, PATIENT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.recommend(TENANT_ID, PATIENT_ID, "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheduleType");
    }

    @Test
    void rejectsMissingPatientAcrossTenantBoundaries() {
        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recommend(TENANT_ID, PATIENT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Patient not found");
    }

    private PatientEntity mockPatient(LocalDate dob, Integer ageYears) {
        PatientEntity patient = mock(PatientEntity.class);
        when(patient.getId()).thenReturn(PATIENT_ID);
        when(patient.getDateOfBirth()).thenReturn(dob);
        when(patient.getAgeYears()).thenReturn(ageYears);
        return patient;
    }

    private VaccineMasterEntity vaccine(
            String name,
            String group,
            Integer minAgeDays,
            Integer recommendedAgeDays,
            Integer maxAgeDays,
            String recommendationPolicy,
            String catchUpPolicy,
            Integer catchUpMaxAgeDays,
            String applicableAgeGroup,
            boolean recurring,
            Integer recurrenceDays,
            Integer boosterGapDays
    ) {
        VaccineMasterEntity vaccine = mock(VaccineMasterEntity.class);
        when(vaccine.getId()).thenReturn(UUID.nameUUIDFromBytes((name + group).getBytes()));
        when(vaccine.getVaccineName()).thenReturn(name);
        when(vaccine.getVaccineGroup()).thenReturn(group);
        when(vaccine.getDoseNumber()).thenReturn(1);
        when(vaccine.getRoute()).thenReturn("IM");
        when(vaccine.getAdministrationSite()).thenReturn("Deltoid");
        when(vaccine.getScheduleType()).thenReturn("CLINIC_CUSTOM");
        when(vaccine.getMinAgeDays()).thenReturn(minAgeDays);
        when(vaccine.getRecommendedAgeDays()).thenReturn(recommendedAgeDays);
        when(vaccine.getMaxAgeDays()).thenReturn(maxAgeDays);
        when(vaccine.getGapDays()).thenReturn(recommendedAgeDays);
        when(vaccine.getRecommendedGapDays()).thenReturn(recommendedAgeDays);
        when(vaccine.getBoosterGapDays()).thenReturn(boosterGapDays);
        when(vaccine.getBoosterRules()).thenReturn("Annual booster");
        when(vaccine.isRecurring()).thenReturn(recurring);
        when(vaccine.getRecurrenceDays()).thenReturn(recurrenceDays);
        when(vaccine.getRecommendationPolicy()).thenReturn(recommendationPolicy);
        when(vaccine.getCatchUpPolicy()).thenReturn(catchUpPolicy);
        when(vaccine.getCatchUpMaxAgeDays()).thenReturn(catchUpMaxAgeDays);
        when(vaccine.getApplicableAgeGroup()).thenReturn(applicableAgeGroup);
        when(vaccine.getAgeGroup()).thenReturn(applicableAgeGroup);
        when(vaccine.isActive()).thenReturn(true);
        return vaccine;
    }

    private PatientVaccinationEntity vaccination(VaccineMasterEntity vaccine, LocalDate givenDate, Integer doseNumber) {
        PatientVaccinationEntity vaccination = mock(PatientVaccinationEntity.class);
        UUID vaccineId = vaccine.getId();
        String vaccineNameSnapshot = vaccine.getVaccineName();
        when(vaccination.getVaccineId()).thenReturn(vaccineId);
        when(vaccination.getVaccineNameSnapshot()).thenReturn(vaccineNameSnapshot == null ? null : vaccineNameSnapshot.toUpperCase(Locale.ROOT));
        when(vaccination.getDoseNumber()).thenReturn(doseNumber);
        when(vaccination.getGivenDate()).thenReturn(givenDate);
        when(vaccination.getCreatedAt()).thenReturn(null);
        return vaccination;
    }
}
