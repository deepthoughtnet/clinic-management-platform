package com.deepthoughtnet.clinic.api.patientportal.aivav2.language;

/** Language-specific normalization boundary for typed AIVA turns. */
public interface AivaLanguageAdapter {
    String languageCode();

    boolean supports(String requestedLanguage, String rawText);

    NormalizedUserTurn normalize(String rawText);

    default NormalizedUserTurn normalize(String rawText, java.time.LocalDate referenceDate) {
        return normalize(rawText);
    }
}
