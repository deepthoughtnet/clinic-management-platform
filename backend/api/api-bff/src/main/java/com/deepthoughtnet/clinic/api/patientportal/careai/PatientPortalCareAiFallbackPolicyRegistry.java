package com.deepthoughtnet.clinic.api.patientportal.careai;

enum PatientPortalCareAiFallbackAction {
    NONE,
    OFFER_ALTERNATIVE,
    RETRY_OR_CONTACT,
    RECONCILE_WRITE,
    CALL_TO_BOOK,
    FAIL_SAFE
}

final class PatientPortalCareAiFallbackPolicyRegistry {
    PatientPortalCareAiFallbackAction actionFor(String skillId, PatientPortalCareAiSkillOutcome outcome) {
        if (skillId == null || outcome == null) {
            return PatientPortalCareAiFallbackAction.FAIL_SAFE;
        }
        return switch (skillId + ":" + outcome) {
            case "doctor.find:NO_MATCH", "availability.check:NO_MATCH" -> PatientPortalCareAiFallbackAction.OFFER_ALTERNATIVE;
            case "doctor.find:NOT_BOOKABLE" -> PatientPortalCareAiFallbackAction.CALL_TO_BOOK;
            case "doctor.find:TIMEOUT", "doctor.find:TEMPORARILY_UNAVAILABLE",
                 "availability.check:TIMEOUT", "availability.check:TEMPORARILY_UNAVAILABLE" -> PatientPortalCareAiFallbackAction.RETRY_OR_CONTACT;
            case "appointment.book:TIMEOUT", "appointment.cancel:TIMEOUT", "appointment.reschedule:TIMEOUT" -> PatientPortalCareAiFallbackAction.RECONCILE_WRITE;
            default -> switch (outcome) {
                case SUCCESS, MULTIPLE_MATCHES, NEEDS_INPUT, NEEDS_CONFIRMATION, NOT_AUTHORIZED,
                        CANCELLED, STALE, RUNNING -> PatientPortalCareAiFallbackAction.NONE;
                default -> PatientPortalCareAiFallbackAction.FAIL_SAFE;
            };
        };
    }
}
