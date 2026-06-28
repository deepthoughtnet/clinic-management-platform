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
