package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import java.time.LocalDate;
import java.util.List;

record AivaV2TemporalResolution(
        Status status,
        LocalDate localDate,
        Source source,
        String rawCandidate,
        String modelCandidate,
        boolean modelVsDeterministicMismatch,
        List<LocalDate> candidateDates
) {
    AivaV2TemporalResolution(Status status, LocalDate localDate, Source source,
                             String rawCandidate, String modelCandidate,
                             boolean modelVsDeterministicMismatch) {
        this(status, localDate, source, rawCandidate, modelCandidate,
                modelVsDeterministicMismatch, List.of());
    }

    AivaV2TemporalResolution {
        candidateDates = candidateDates == null ? List.of() : List.copyOf(candidateDates);
    }

    enum Status { RESOLVED, AMBIGUOUS, INVALID, UNCHANGED }

    enum Source { CURRENT_TURN_EXPLICIT, MODEL_CANONICAL, PREVIOUS_CONTEXT }

    static AivaV2TemporalResolution unchanged(LocalDate previous, String modelCandidate) {
        return new AivaV2TemporalResolution(Status.UNCHANGED, previous, Source.PREVIOUS_CONTEXT,
                null, modelCandidate, false, List.of());
    }
}
