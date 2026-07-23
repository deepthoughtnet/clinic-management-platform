package com.deepthoughtnet.clinic.prescription.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable notification-safe payload emitted when a prescription becomes patient-visible.
 */
public record PrescriptionReadyEventPayload(
        UUID prescriptionId,
        UUID consultationId,
        UUID patientId,
        UUID doctorUserId,
        String doctorDisplayName,
        String clinicDisplayName,
        String prescriptionNumber,
        LocalDate followUpDate,
        String timezone,
        OffsetDateTime finalizedAt,
        int versionNumber
) implements ModuleBusinessEventPayload {
}
