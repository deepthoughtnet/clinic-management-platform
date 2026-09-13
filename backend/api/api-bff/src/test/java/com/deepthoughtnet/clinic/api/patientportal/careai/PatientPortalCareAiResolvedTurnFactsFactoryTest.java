package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatientPortalCareAiResolvedTurnFactsFactoryTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-09T12:00:00Z"), ZoneId.of("Asia/Kolkata"));
    private final PatientPortalCareAiEntityRegistry registry = new PatientPortalCareAiEntityRegistry();
    private final PatientPortalCareAiResolvedTurnFactsFactory factory = new PatientPortalCareAiResolvedTurnFactsFactory(
            new DoctorResolver(registry), new SpecialtyResolver(registry), new ClinicResolver(registry),
            new SelectionResolver(), CLOCK, () -> ZoneId.of("Asia/Kolkata"));

    @Test
    void resolvesCanonicalProviderSpecialityAndSelectionFacts() {
        PatientPortalCareAiCanonicalTurn turn = turn("Dr Akshu", "General Physician", "second", null, null);
        PatientPortalCareAiResolvedTurnFacts facts = factory.resolve(
                turn,
                List.of(
                        new CanonicalEntityCandidate("d1", "Akshu Kumar", "Dr Akshu Kumar", List.of("Akshu")),
                        new CanonicalEntityCandidate("d2", "Other Doctor", "Other Doctor", List.of())),
                List.of("General Medicine"),
                List.of(),
                List.of(
                        new CanonicalEntityCandidate("d1", "Akshu Kumar", "Dr Akshu Kumar", List.of("Akshu")),
                        new CanonicalEntityCandidate("d2", "Other Doctor", "Other Doctor", List.of())),
                null, null);

        assertThat(facts.doctor().status()).isEqualTo(CanonicalResolutionStatus.RESOLVED);
        assertThat(facts.doctor().candidateIds()).containsExactly("d1");
        assertThat(facts.speciality().canonicalValue()).isEqualTo("General Medicine");
        assertThat(facts.selection().status()).isEqualTo(CanonicalResolutionStatus.RESOLVED);
        assertThat(facts.selection().candidateIds()).containsExactly("d2");
    }

    @Test
    void preservesExplicitUnresolvedAndAmbiguousOutcomes() {
        PatientPortalCareAiResolvedTurnFacts unresolved = factory.resolve(
                turn("Unknown Doctor", null, null, null, null),
                List.of(new CanonicalEntityCandidate("d1", "Akshu Kumar", "Akshu Kumar", List.of())),
                List.of("General Medicine"), List.of(), List.of(), null, null);
        PatientPortalCareAiResolvedTurnFacts ambiguous = factory.resolve(
                turn("Aksh", null, null, null, null),
                List.of(
                        new CanonicalEntityCandidate("d1", "Akshu Kumar", "Akshu Kumar", List.of("Aksh")),
                        new CanonicalEntityCandidate("d2", "Akshita Rao", "Akshita Rao", List.of("Aksh"))),
                List.of("General Medicine"), List.of(), List.of(), null, null);

        assertThat(unresolved.doctor().status()).isEqualTo(CanonicalResolutionStatus.UNRESOLVED);
        assertThat(ambiguous.doctor().status()).isEqualTo(CanonicalResolutionStatus.AMBIGUOUS);
    }

    @Test
    void resolvesEquivalentDatesWithOneReferenceClock() {
        PatientPortalCareAiTemporalResolver resolver = new PatientPortalCareAiTemporalResolver(CLOCK, ZoneId.of("Asia/Kolkata"));

        assertThat(resolver.resolveDate("next Monday")).contains(LocalDate.of(2026, 9, 14));
        assertThat(resolver.resolveDate("Appointment for next Monday"))
                .contains(LocalDate.of(2026, 9, 14));
        assertThat(resolver.resolveDate("कल")).contains(LocalDate.of(2026, 9, 10));
    }

    private PatientPortalCareAiCanonicalTurn turn(String doctor, String speciality, String selection,
                                                   String date, String time) {
        return new PatientPortalCareAiCanonicalTurn(
                PatientPortalCareAiDialogAct.PROVIDE_INFORMATION,
                PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                new PatientPortalCareAiCanonicalEntities(doctor, null, speciality, null, null, date, time, null, selection),
                PatientPortalCareAiConfirmationPolarity.NONE,
                PatientPortalCareAiCorrection.none(), PatientPortalCareAiAlternativeRequest.none(),
                new PatientPortalCareAiSelectionReference(selection != null, 2, "doctor"), false, false,
                PatientPortalCareAiInterpretationSource.FAST_PATH, 1.0d);
    }
}
