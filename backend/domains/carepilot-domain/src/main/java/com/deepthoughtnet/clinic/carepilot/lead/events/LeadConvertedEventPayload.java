package com.deepthoughtnet.clinic.carepilot.lead.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.util.UUID;

/**
 * Immutable lead-converted payload owned by the Engage module.
 */
public record LeadConvertedEventPayload(
        UUID leadId,
        UUID patientId,
        boolean createdNewPatient,
        UUID bookedAppointmentId
) implements ModuleBusinessEventPayload {
}
