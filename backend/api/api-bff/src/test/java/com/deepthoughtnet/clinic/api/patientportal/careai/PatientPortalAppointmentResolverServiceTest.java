package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatientPortalAppointmentResolverServiceTest {
    private final PatientPortalAppointmentResolverService resolverService = new PatientPortalAppointmentResolverService();

    @Test
    void resolvesAppointmentByDoctorDateAndTimeCombination() {
        LocalDate date = LocalDate.of(2026, 6, 28);
        PatientPortalCareAiAppointmentOption target = appointment("Dr Neha Mehta", date, LocalTime.of(16, 30));
        List<PatientPortalCareAiAppointmentOption> appointments = List.of(
                appointment("Dr Ashish Shri", date, LocalTime.of(9, 0)),
                target,
                appointment("Dr Vikas Singh", date.plusDays(1), LocalTime.of(10, 0))
        );

        var resolution = resolverService.resolve(
                appointments,
                "Dr Neha Mehta on 28 June 2026 at 4:30 PM",
                "en",
                new PatientPortalCareAiEntityExtractor(new PatientPortalCareAiEntityRegistry())
                        .extract("Dr Neha Mehta on 28 June 2026 at 4:30 PM", "en")
        );

        assertThat(resolution.resolved()).isTrue();
        assertThat(resolution.appointment()).isEqualTo(target);
    }

    @Test
    void resolvesAppointmentByHindiOrdinalNumber() {
        List<PatientPortalCareAiAppointmentOption> appointments = List.of(
                appointment("Dr Ashish Shri", LocalDate.of(2026, 6, 28), LocalTime.of(9, 0)),
                appointment("Dr Neha Mehta", LocalDate.of(2026, 6, 28), LocalTime.of(16, 30)),
                appointment("Dr Vikas Singh", LocalDate.of(2026, 6, 29), LocalTime.of(10, 0))
        );

        var resolution = resolverService.resolve(appointments, "तीसरा", "hi-IN", PatientPortalCareAiExtractedEntities.empty());

        assertThat(resolution.resolved()).isTrue();
        assertThat(resolution.appointment().doctorName()).isEqualTo("Dr Vikas Singh");
    }

    @Test
    void resolvesMultipleMatchAsClarification() {
        LocalDate date = LocalDate.of(2026, 6, 28);
        List<PatientPortalCareAiAppointmentOption> appointments = List.of(
                appointment("Dr Neha Mehta", date, LocalTime.of(9, 0)),
                appointment("Dr Neha Mehta", date, LocalTime.of(16, 30))
        );

        var resolution = resolverService.resolve(appointments, "Neha", "en", PatientPortalCareAiExtractedEntities.empty());

        assertThat(resolution.resolved()).isFalse();
        assertThat(resolution.status()).isEqualTo(PatientPortalAppointmentResolverService.ResolutionStatus.MULTIPLE);
        assertThat(resolution.prompt()).contains("doctor name, date, time, or list number");
        assertThat(resolution.matches()).hasSize(2);
    }

    @Test
    void resolvesNoMatchPolitely() {
        List<PatientPortalCareAiAppointmentOption> appointments = List.of(
                appointment("Dr Ashish Shri", LocalDate.of(2026, 6, 28), LocalTime.of(9, 0))
        );

        var resolution = resolverService.resolve(appointments, "Dr Unknown", "en", PatientPortalCareAiExtractedEntities.empty());

        assertThat(resolution.resolved()).isFalse();
        assertThat(resolution.status()).isEqualTo(PatientPortalAppointmentResolverService.ResolutionStatus.NONE);
        assertThat(resolution.prompt()).contains("could not find a matching appointment");
    }

    @Test
    void resolvesBySingleDoctorDateAndTimeWithExtractedEntities() {
        LocalDate date = LocalDate.of(2026, 6, 28);
        PatientPortalCareAiAppointmentOption target = appointment("Dr Neha Mehta", date, LocalTime.of(16, 30));
        var resolution = resolverService.resolve(
                List.of(target, appointment("Dr Ashish Shri", date, LocalTime.of(9, 0))),
                "Doctor Neha Mehta 28 June 2026 16:30",
                "en",
                new PatientPortalCareAiEntityExtractor(new PatientPortalCareAiEntityRegistry())
                        .extract("Doctor Neha Mehta 28 June 2026 16:30", "en")
        );

        assertThat(resolution.resolved()).isTrue();
        assertThat(resolution.appointment()).isEqualTo(target);
    }

    private PatientPortalCareAiAppointmentOption appointment(String doctorName, LocalDate date, LocalTime time) {
        return new PatientPortalCareAiAppointmentOption(
                UUID.randomUUID(),
                UUID.randomUUID(),
                doctorName,
                UUID.randomUUID(),
                "Sunrise Clinic",
                date,
                time,
                "BOOKED",
                "Follow-up review"
        );
    }
}
