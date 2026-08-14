package com.deepthoughtnet.clinic.patient.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PatientPortalAccessRequestRecord(
        UUID id,
        UUID tenantId,
        String tenantCode,
        String tenantName,
        PatientPortalAccessRequestType requestType,
        String fullName,
        String mobile,
        String email,
        String note,
        PatientPortalAccessRequestStatus status,
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
