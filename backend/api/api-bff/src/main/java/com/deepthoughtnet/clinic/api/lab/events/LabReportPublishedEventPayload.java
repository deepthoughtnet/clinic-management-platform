package com.deepthoughtnet.clinic.api.lab.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable lab-report-published payload owned by the lab module.
 */
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
}
