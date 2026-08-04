package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

public record PatientPortalAppointmentConfirmationResponse(
        String appointmentReference,
        String bookingStatus,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String timezone,
        String doctorName,
        String clinicName,
        String practiceLocation,
        String consultationMode,
        String source,
        String status,
        String reason,
        String message,
        BigDecimal fee,
        String patientDisplayName,
        boolean tenantRelationshipCreated,
        OffsetDateTime createdAt,
        String notificationStatusSummary
) {
    public PatientPortalAppointmentConfirmationResponse(
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String doctorName,
            String clinicName,
            String source,
            String status,
            String reason,
            String message
    ) {
        this(
                null,
                status,
                appointmentDate,
                appointmentTime,
                null,
                doctorName,
                clinicName,
                null,
                null,
                source,
                status,
                reason,
                message,
                null,
                null,
                false,
                null,
                null
        );
    }

    public PatientPortalAppointmentConfirmationResponse(
            String appointmentReference,
            String bookingStatus,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String timezone,
            String doctorName,
            String clinicName,
            String practiceLocation,
            String consultationMode,
            String source,
            String status,
            String reason,
            String message,
            BigDecimal fee,
            String patientDisplayName,
            boolean tenantRelationshipCreated,
            OffsetDateTime createdAt,
            String notificationStatusSummary
    ) {
        this.appointmentReference = appointmentReference;
        this.bookingStatus = bookingStatus;
        this.appointmentDate = appointmentDate;
        this.appointmentTime = appointmentTime;
        this.timezone = timezone;
        this.doctorName = doctorName;
        this.clinicName = clinicName;
        this.practiceLocation = practiceLocation;
        this.consultationMode = consultationMode;
        this.source = source;
        this.status = status;
        this.reason = reason;
        this.message = message;
        this.fee = fee;
        this.patientDisplayName = patientDisplayName;
        this.tenantRelationshipCreated = tenantRelationshipCreated;
        this.createdAt = createdAt;
        this.notificationStatusSummary = notificationStatusSummary;
    }
}
