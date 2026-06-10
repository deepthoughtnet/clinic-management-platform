package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentRecord(
        UUID id,
        UUID tenantId,
        UUID patientId,
        String patientNumber,
        String patientName,
        String patientMobile,
        UUID doctorUserId,
        String doctorName,
        UUID consultationId,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        Integer tokenNumber,
        String displayReference,
        String reason,
        AppointmentType type,
        AppointmentPriority priority,
        AppointmentStatus status,
        String paymentBypassReason,
        String paymentBypassNotes,
        UUID paymentBypassedBy,
        OffsetDateTime paymentBypassedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public AppointmentRecord(
            UUID id,
            UUID tenantId,
            UUID patientId,
            String patientNumber,
            String patientName,
            String patientMobile,
            UUID doctorUserId,
            String doctorName,
            UUID consultationId,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            Integer tokenNumber,
            String reason,
            AppointmentType type,
            AppointmentPriority priority,
            AppointmentStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(
                id,
                tenantId,
                patientId,
                patientNumber,
                patientName,
                patientMobile,
                doctorUserId,
                doctorName,
                consultationId,
                appointmentDate,
                appointmentTime,
                tokenNumber,
                null,
                reason,
                type,
                priority,
                status,
                null,
                null,
                null,
                null,
                createdAt,
                updatedAt
        );
    }

    public AppointmentRecord(
            UUID id,
            UUID tenantId,
            UUID patientId,
            String patientNumber,
            String patientName,
            String patientMobile,
            UUID doctorUserId,
            String doctorName,
            UUID consultationId,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            Integer tokenNumber,
            String displayReference,
            String reason,
            AppointmentType type,
            AppointmentPriority priority,
            AppointmentStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(
                id,
                tenantId,
                patientId,
                patientNumber,
                patientName,
                patientMobile,
                doctorUserId,
                doctorName,
                consultationId,
                appointmentDate,
                appointmentTime,
                tokenNumber,
                displayReference,
                reason,
                type,
                priority,
                status,
                null,
                null,
                null,
                null,
                createdAt,
                updatedAt
        );
    }
}
