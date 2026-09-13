package com.deepthoughtnet.clinic.api.patientportal.careai;

record PatientPortalCareAiSkillResult<T>(
        PatientPortalCareAiSkillOutcome outcome,
        T value,
        String message,
        int candidateCount,
        boolean confirmationRequired
) {
    static <T> PatientPortalCareAiSkillResult<T> success(T value, String message) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.SUCCESS, value, message, value == null ? 0 : 1, false);
    }

    static <T> PatientPortalCareAiSkillResult<T> success(T value, String message, int candidateCount) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.SUCCESS, value, message, candidateCount, false);
    }

    static <T> PatientPortalCareAiSkillResult<T> noMatch(String message) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.NO_MATCH, null, message, 0, false);
    }

    static <T> PatientPortalCareAiSkillResult<T> multipleMatches(T value, String message, int candidateCount) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.MULTIPLE_MATCHES, value, message, candidateCount, false);
    }

    static <T> PatientPortalCareAiSkillResult<T> needsInput(String message) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.NEEDS_INPUT, null, message, 0, false);
    }

    static <T> PatientPortalCareAiSkillResult<T> needsConfirmation(T value, String message) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.NEEDS_CONFIRMATION, value, message, value == null ? 0 : 1, true);
    }

    static <T> PatientPortalCareAiSkillResult<T> notAuthorized(String message) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.NOT_AUTHORIZED, null, message, 0, false);
    }

    static <T> PatientPortalCareAiSkillResult<T> notBookable(String message) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.NOT_BOOKABLE, null, message, 0, false);
    }

    static <T> PatientPortalCareAiSkillResult<T> failed(String message) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.FAILED, null, message, 0, false);
    }

    static <T> PatientPortalCareAiSkillResult<T> running(String message) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.RUNNING, null, message, 0, false);
    }

    static <T> PatientPortalCareAiSkillResult<T> timeout(String message) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.TIMEOUT, null, message, 0, false);
    }

    static <T> PatientPortalCareAiSkillResult<T> temporarilyUnavailable(String message) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.TEMPORARILY_UNAVAILABLE, null, message, 0, false);
    }

    static <T> PatientPortalCareAiSkillResult<T> cancelled(String message) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.CANCELLED, null, message, 0, false);
    }

    static <T> PatientPortalCareAiSkillResult<T> stale(String message) {
        return new PatientPortalCareAiSkillResult<>(PatientPortalCareAiSkillOutcome.STALE, null, message, 0, false);
    }
}
