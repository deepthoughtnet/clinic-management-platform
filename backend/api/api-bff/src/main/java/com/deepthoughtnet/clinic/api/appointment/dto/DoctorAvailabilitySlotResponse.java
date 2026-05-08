package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotStatus;
import java.time.LocalDate;
import java.time.LocalTime;

public record DoctorAvailabilitySlotResponse(
        String doctorUserId,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime slotTime,
        LocalTime slotEndTime,
        DoctorAvailabilitySlotStatus status,
        int bookedCount,
        int maxPatientsPerSlot,
        boolean selectable,
        String appointmentId,
        String patientId,
        String patientNumber,
        String patientName,
        Integer tokenNumber,
        AppointmentStatus appointmentStatus,
        String reason
) {
}
