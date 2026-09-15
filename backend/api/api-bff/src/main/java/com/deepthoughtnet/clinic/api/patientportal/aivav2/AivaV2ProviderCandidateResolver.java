package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSearchResult;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.util.StringUtils;

/** Resolves bounded user selections against the server-held provider result. */
final class AivaV2ProviderCandidateResolver {
    private AivaV2ProviderCandidateResolver() { }

    static Optional<ProviderCandidate> byOrdinal(ProviderSearchResult result, int ordinal) {
        if (result == null || ordinal < 1 || ordinal > result.candidates().size()) return Optional.empty();
        return Optional.of(result.candidates().get(ordinal - 1));
    }

    static Optional<ProviderCandidate> byDisplayedText(ProviderSearchResult result, String text) {
        if (result == null || !StringUtils.hasText(text)) return Optional.empty();
        String query = normalizeDoctorName(stripSelectionWrapper(text));
        if (!StringUtils.hasText(query)) return Optional.empty();
        List<ProviderCandidate> matches = result.candidates().stream()
                .filter(candidate -> normalizeDoctorName(candidate.displayName()).equals(query)
                        || normalizeDoctorName(candidate.displayName()).contains(query))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private static String stripSelectionWrapper(String value) {
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceFirst("^(?:book|select|choose|take)\\s+(?:with\\s+)?", "")
                .replaceFirst("^with\\s+", "")
                .replaceAll("[?.!,]+$", "").trim();
    }

    private static String normalizeDoctorName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim()
                .replaceFirst("^(doctor|dr|doc)\\s+", "");
    }
}
