package com.deepthoughtnet.clinic.api.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class AppointmentTimingRulesTest {

    @Test
    void todaySlotsBeforeCurrentLocalTimeAreNotBookable() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.of(2026, 6, 28, 10, 45, 0, 0, zone);

        assertThat(AppointmentTimingRules.isBookableForPatient(LocalDate.of(2026, 6, 28), LocalTime.of(9, 0), zone, now)).isFalse();
        assertThat(AppointmentTimingRules.isBookableForPatient(LocalDate.of(2026, 6, 28), LocalTime.of(11, 0), zone, now)).isTrue();
    }

    @Test
    void upcomingAppointmentFilteringUsesFullLocalDateTimeAcrossMidnight() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime justAfterMidnight = ZonedDateTime.of(2026, 10, 26, 0, 5, 0, 0, zone);
        ZonedDateTime previousDayAppointment = justAfterMidnight.minusHours(2);
        ZonedDateTime laterSameDayAppointment = justAfterMidnight.plusHours(2);

        assertThat(previousDayAppointment.toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(previousDayAppointment.toLocalTime()).isEqualTo(LocalTime.of(22, 5));
        assertThat(AppointmentTimingRules.isUpcoming(previousDayAppointment.toLocalDate(),
                previousDayAppointment.toLocalTime(), zone, justAfterMidnight)).isFalse();
        assertThat(AppointmentTimingRules.isUpcoming(laterSameDayAppointment.toLocalDate(),
                laterSameDayAppointment.toLocalTime(), zone, justAfterMidnight)).isTrue();

        ZonedDateTime beforeMidnight = ZonedDateTime.of(2026, 10, 25, 23, 55, 0, 0, zone);
        ZonedDateTime afterSlot = ZonedDateTime.of(2026, 10, 26, 0, 6, 0, 0, zone);
        assertThat(AppointmentTimingRules.isUpcoming(LocalDate.of(2026, 10, 26), LocalTime.of(0, 5),
                zone, beforeMidnight)).isTrue();
        assertThat(AppointmentTimingRules.isUpcoming(LocalDate.of(2026, 10, 26), LocalTime.of(0, 5),
                zone, afterSlot)).isFalse();
    }

    @Test
    void pastDateReturnsNotBookableAndFutureDateRemainsBookable() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime now = ZonedDateTime.of(2026, 6, 28, 10, 45, 0, 0, zone);

        assertThat(AppointmentTimingRules.isBookableForPatient(LocalDate.of(2026, 6, 27), LocalTime.of(10, 0), zone, now)).isFalse();
        assertThat(AppointmentTimingRules.isBookableForPatient(LocalDate.of(2026, 6, 29), LocalTime.of(10, 0), zone, now)).isTrue();
    }

    @Test
    void nullZoneFallsBackToIndiaTimeZone() {
        ZonedDateTime now = ZonedDateTime.of(2026, 6, 28, 10, 45, 0, 0, ZoneId.of("Asia/Kolkata"));

        assertThat(AppointmentTimingRules.isBookableForPatient(LocalDate.of(2026, 6, 28), LocalTime.of(10, 0), null, now)).isFalse();
        assertThat(AppointmentTimingRules.isBookableForPatient(LocalDate.of(2026, 6, 28), LocalTime.of(11, 0), null, now)).isTrue();
    }
}
