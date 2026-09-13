package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.Duration;

final class PatientPortalCareAiWaitingPolicy {
    static final Duration ACKNOWLEDGEMENT_THRESHOLD = Duration.ofMillis(700);

    boolean shouldAcknowledge(Duration elapsed) {
        return elapsed != null && !elapsed.minus(ACKNOWLEDGEMENT_THRESHOLD).isNegative();
    }

    String acknowledgement(String skillId) {
        return switch (skillId) {
            case "doctor.find" -> "Let me check available doctors.";
            case "availability.check" -> "Let me check available times.";
            case "appointment.book" -> "I'm confirming that appointment.";
            case "appointment.cancel" -> "I'm processing the cancellation.";
            case "appointment.reschedule" -> "I'm checking the new appointment time.";
            default -> "Let me check that for you.";
        };
    }
}
