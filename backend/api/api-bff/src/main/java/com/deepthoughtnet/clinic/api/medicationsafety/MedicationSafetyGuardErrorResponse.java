package com.deepthoughtnet.clinic.api.medicationsafety;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MedicationSafetyGuardErrorResponse(
        OffsetDateTime timestamp,
        String path,
        int status,
        String code,
        String message,
        String correlationId,
        String requestId,
        UUID prescriptionId,
        String evaluationStatus,
        String requiredAction,
        List<String> findingIds
) {
}
