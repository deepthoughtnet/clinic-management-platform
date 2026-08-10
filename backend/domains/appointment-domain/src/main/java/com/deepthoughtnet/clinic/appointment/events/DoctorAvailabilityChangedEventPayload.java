package com.deepthoughtnet.clinic.appointment.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record DoctorAvailabilityChangedEventPayload(
        UUID availabilityId,
        UUID doctorUserId,
        String action,
        String reason,
        boolean active,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) implements ModuleBusinessEventPayload {
}
