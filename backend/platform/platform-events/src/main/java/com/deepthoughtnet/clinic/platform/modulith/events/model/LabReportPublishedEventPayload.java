package com.deepthoughtnet.clinic.platform.modulith.events.model;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.util.UUID;

public record LabReportPublishedEventPayload(
        UUID labOrderId,
        UUID patientId,
        UUID consultationId,
        String reportFilename,
        String deliveryStatus
) implements ModuleBusinessEventPayload {
}
