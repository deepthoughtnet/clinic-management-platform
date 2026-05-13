package com.deepthoughtnet.clinic.carepilot.webinar.registration;

import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationStatus;

/** Command to mark attendance outcome for one registration. */
public record WebinarAttendanceCommand(
        WebinarRegistrationStatus registrationStatus,
        String notes
) {}
