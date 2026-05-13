package com.deepthoughtnet.clinic.carepilot.webinar.registration;

import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationSource;
import java.util.UUID;

/** Command to create a webinar registration. */
public record WebinarRegistrationCommand(
        UUID patientId,
        UUID leadId,
        String attendeeName,
        String attendeeEmail,
        String attendeePhone,
        WebinarRegistrationSource source,
        String notes
) {}
