package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record DoctorAvailabilitySlotRecord(
        UUID doctorUserId,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime slotTime,
        LocalTime slotEndTime,
        DoctorAvailabilitySlotStatus status,
        int bookedCount,
        int maxPatientsPerSlot,
        boolean selectable,
        UUID appointmentId,
        UUID patientId,
        String patientNumber,
        String patientName,
        Integer tokenNumber,
        AppointmentStatus appointmentStatus,
        String reason
) {
}
