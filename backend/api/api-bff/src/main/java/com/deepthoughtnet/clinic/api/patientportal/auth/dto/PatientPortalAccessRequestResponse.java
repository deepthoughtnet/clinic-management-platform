package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PatientPortalAccessRequestResponse(
        UUID id,
        UUID tenantId,
        String tenantCode,
        String tenantName,
        String requestType,
        String fullName,
        String mobile,
        String email,
        String note,
        String status,
        String rejectionReason,
        UUID linkedPatientId,
        String linkedPatientDisplayName,
        UUID reviewedBy,
        String reviewedByDisplayName,
        String temporaryAccessCode,
        OffsetDateTime requestedAt,
        OffsetDateTime reviewedAt,
        OffsetDateTime approvedAt,
        OffsetDateTime activatedAt,
        OffsetDateTime revokedAt,
        OffsetDateTime accessCodeExpiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version
) {
}
