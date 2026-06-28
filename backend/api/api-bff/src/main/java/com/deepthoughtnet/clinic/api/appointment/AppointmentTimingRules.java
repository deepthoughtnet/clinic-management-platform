package com.deepthoughtnet.clinic.api.appointment;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

public final class AppointmentTimingRules {
    private static final Duration ACTIONABLE_GRACE = Duration.ofHours(1);
    private static final Duration NO_SHOW_GRACE = Duration.ofHours(4);

    private AppointmentTimingRules() {}

    public static boolean isUpcoming(AppointmentRecord appointment, ZoneId zone) {
        return appointment != null && isUpcoming(appointment.appointmentDate(), appointment.appointmentTime(), zone);
    }

    public static boolean isUpcoming(LocalDate appointmentDate, LocalTime appointmentTime, ZoneId zone) {
        return isUpcoming(appointmentDate, appointmentTime, zone, ZonedDateTime.now(resolveZone(zone)));
    }

    public static boolean isUpcoming(LocalDate appointmentDate, LocalTime appointmentTime, ZoneId zone, ZonedDateTime now) {
        ZoneId resolvedZone = resolveZone(zone);
        ZonedDateTime referenceNow = now == null ? ZonedDateTime.now(resolvedZone) : now.withZoneSameInstant(resolvedZone);
        ZonedDateTime start = appointmentStart(appointmentDate, appointmentTime, resolvedZone);
        return start != null && !start.isBefore(referenceNow);
    }

    public static boolean isBookableForPatient(LocalDate appointmentDate, LocalTime appointmentTime, ZoneId zone, ZonedDateTime now) {
        return isSlotBookableForPatient(appointmentDate, appointmentTime, zone, now);
    }

    public static boolean isSlotBookableForPatient(LocalDate appointmentDate, LocalTime appointmentTime, ZoneId zone, ZonedDateTime now) {
        return isUpcoming(appointmentDate, appointmentTime, zone, now);
    }

    public static boolean isActionable(AppointmentRecord appointment, ZoneId zone) {
        return appointment != null && isActionable(appointment.appointmentDate(), appointment.appointmentTime(), zone);
    }

    public static boolean isActionable(LocalDate appointmentDate, LocalTime appointmentTime, ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(resolveZone(zone));
        ZonedDateTime start = appointmentStart(appointmentDate, appointmentTime, now.getZone());
        return start != null && !now.isAfter(start.plus(ACTIONABLE_GRACE));
    }

    public static boolean isNoShowEligible(AppointmentRecord appointment, ZoneId zone) {
        return appointment != null && isNoShowEligible(appointment.appointmentDate(), appointment.appointmentTime(), zone);
    }

    public static boolean isNoShowEligible(LocalDate appointmentDate, LocalTime appointmentTime, ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(resolveZone(zone));
        ZonedDateTime start = appointmentStart(appointmentDate, appointmentTime, now.getZone());
        return start != null && !now.isBefore(start.plus(NO_SHOW_GRACE));
    }

    private static ZonedDateTime appointmentStart(LocalDate appointmentDate, LocalTime appointmentTime, ZoneId zone) {
        if (appointmentDate == null) {
            return null;
        }
        LocalTime safeTime = appointmentTime == null ? LocalTime.MAX : appointmentTime;
        return appointmentDate.atTime(safeTime).atZone(resolveZone(zone));
    }

    private static ZoneId resolveZone(ZoneId zone) {
        return Objects.requireNonNullElse(zone, ZoneId.of("Asia/Kolkata"));
    }
}
