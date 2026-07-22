package com.deepthoughtnet.clinic.platform.modulith.events.model;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import java.util.UUID;

public record LeadConvertedEventPayload(
        UUID leadId,
        UUID patientId,
        boolean createdNewPatient,
        UUID bookedAppointmentId
) implements ModuleBusinessEventPayload {
}
