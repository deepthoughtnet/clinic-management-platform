package com.deepthoughtnet.clinic.platform.modulith.events.model;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LabReportPublishedEventPayload(
        UUID labOrderId,
        UUID patientId,
        UUID consultationId,
        String orderNumber,
        String clinicDisplayName,
        String timezone,
        OffsetDateTime publishedAt,
        String reportFilename,
        String deliveryStatus
) implements ModuleBusinessEventPayload {
    // The notification layer relies on orderNumber, timezone, and publishedAt for patient-safe copy and routing.
}
