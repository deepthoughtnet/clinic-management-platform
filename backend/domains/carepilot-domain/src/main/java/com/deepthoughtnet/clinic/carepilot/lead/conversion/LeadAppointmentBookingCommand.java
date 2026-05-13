package com.deepthoughtnet.clinic.carepilot.lead.conversion;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Optional appointment booking payload to run after lead conversion. */
public record LeadAppointmentBookingCommand(
        UUID doctorUserId,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String reason,
        String notes,
        AppointmentPriority priority
) {}
