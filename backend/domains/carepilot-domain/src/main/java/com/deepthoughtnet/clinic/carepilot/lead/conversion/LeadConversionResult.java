package com.deepthoughtnet.clinic.carepilot.lead.conversion;

import java.util.UUID;

/** Conversion response describing patient linkage and creation mode. */
public record LeadConversionResult(
        UUID leadId,
        UUID patientId,
        boolean newlyCreated,
        UUID appointmentId,
        String appointmentError
) {}
