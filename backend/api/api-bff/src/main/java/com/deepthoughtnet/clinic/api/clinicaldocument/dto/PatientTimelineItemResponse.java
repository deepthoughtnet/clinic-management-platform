package com.deepthoughtnet.clinic.api.clinicaldocument.dto;

public record PatientTimelineItemResponse(
        String id,
        String itemType,
        String title,
        String subtitle,
        String occurredAt,
        String status,
        String documentType,
        String documentId,
        String consultationId,
        String prescriptionId
) {
}
