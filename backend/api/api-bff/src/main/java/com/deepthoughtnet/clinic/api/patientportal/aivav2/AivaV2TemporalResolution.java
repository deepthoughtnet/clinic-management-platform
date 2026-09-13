package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import java.time.LocalDate;

record AivaV2TemporalResolution(
        Status status,
        LocalDate localDate,
        Source source,
        String rawCandidate,
        String modelCandidate,
        boolean modelVsDeterministicMismatch
) {
    enum Status { RESOLVED, AMBIGUOUS, INVALID, UNCHANGED }

    enum Source { CURRENT_TURN_EXPLICIT, MODEL_CANONICAL, PREVIOUS_CONTEXT }

    static AivaV2TemporalResolution unchanged(LocalDate previous, String modelCandidate) {
        return new AivaV2TemporalResolution(Status.UNCHANGED, previous, Source.PREVIOUS_CONTEXT,
                null, modelCandidate, false);
    }
}
