package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DoctorCalendarReconcileResult(
        UUID tenantId,
        int createdCount,
        int skippedCount,
        OffsetDateTime timestamp
) {
}
