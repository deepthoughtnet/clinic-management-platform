package com.deepthoughtnet.clinic.carepilot.lead.model;

import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Read projection for lead management APIs. */
public record LeadRecord(
        UUID id,
        UUID tenantId,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        String email,
        PatientGender gender,
        LocalDate dateOfBirth,
        LeadSource source,
        String sourceDetails,
        UUID campaignId,
        UUID assignedToAppUserId,
        LeadStatus status,
        LeadPriority priority,
        String notes,
        String tags,
        UUID convertedPatientId,
        UUID bookedAppointmentId,
        OffsetDateTime lastContactedAt,
        OffsetDateTime nextFollowUpAt,
        OffsetDateTime lastActivityAt,
        UUID createdBy,
        UUID updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
