package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class CanonicalResolverSupport {
    private static final Pattern NON_WORDS = Pattern.compile("[^\\p{L}\\p{N}\\p{M}:]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Set<String> REFERENTIAL_SELECTION_WORDS = Set.of(
            "that",
            "this",
            "same",
            "the one",
            "that one",
            "this one",
            "that doctor",
            "this doctor",
            "that clinic",
            "this clinic",
            "that service",
            "this service",
            "that location",
            "this location",
            "that slot",
            "this slot"
    );

    String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('’', '\'')
                .replace('“', '"')
                .replace('”', '"');
        String lower = decomposed.toLowerCase(Locale.ROOT).trim();
        String cleaned = NON_WORDS.matcher(lower).replaceAll(" ").trim();
        return WHITESPACE.matcher(cleaned).replaceAll(" ");
    }

    String normalizeName(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized
                .replaceAll("^\\b(?:the|a|an)\\b\\s*", "")
                .replaceAll("\\b(?:doc|dr|doctor)\\b", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    String normalizeClinic(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized
                .replaceAll("^\\b(?:the|a|an)\\b\\s*", "")
                .replaceAll("\\b(?:clinic|hospital|centre|center|branch|branches)\\b", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    String normalizeLocation(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized
                .replaceAll("^\\b(?:the|a|an)\\b\\s*", "")
                .replaceAll("\\b(?:location|area|city|locality|near|around|in|at)\\b", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    String normalizeService(String value) {
        return normalize(value);
    }

    boolean containsPhrase(String text, String phrase) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(phrase)) {
            return false;
        }
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    boolean isReferentialSelection(String normalizedText) {
        if (!StringUtils.hasText(normalizedText)) {
            return false;
        }
        if (REFERENTIAL_SELECTION_WORDS.contains(normalizedText)) {
            return true;
        }
        return normalizedText.startsWith("that ")
                || normalizedText.startsWith("this ")
                || normalizedText.startsWith("same ")
                || normalizedText.equals("that")
                || normalizedText.equals("this");
    }

    Integer parseOrdinalIndex(String normalizedText) {
        if (!StringUtils.hasText(normalizedText)) {
            return null;
        }
        String stripped = normalizedText.trim();
        if (stripped.matches("\\d{1,2}")) {
            try {
                return Integer.parseInt(stripped);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (matchesOrdinal(stripped, List.of("1", "1st", "one", "first", "option one", "doctor one", "slot one", "number one"))) {
            return 1;
        }
        if (matchesOrdinal(stripped, List.of("2", "2nd", "two", "second", "option two", "doctor two", "slot two", "number two"))) {
            return 2;
        }
        if (matchesOrdinal(stripped, List.of("3", "3rd", "three", "third", "option three", "doctor three", "slot three", "number three"))) {
            return 3;
        }
        if (matchesOrdinal(stripped, List.of("4", "4th", "four", "fourth"))) {
            return 4;
        }
        if (matchesOrdinal(stripped, List.of("5", "5th", "five", "fifth"))) {
            return 5;
        }
        if (matchesOrdinal(stripped, List.of("last", "last one", "final", "final one"))) {
            return Integer.MAX_VALUE;
        }
        return null;
    }

    private boolean matchesOrdinal(String text, List<String> candidates) {
        if (!StringUtils.hasText(text) || candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (String candidate : candidates) {
            if (text.equals(candidate) || text.startsWith(candidate + " ")) {
                return true;
            }
        }
        return false;
    }

    String collapseSpaces(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return WHITESPACE.matcher(value.trim()).replaceAll(" ");
    }

    List<String> dedupe(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    List<String> mapLabels(List<CanonicalEntityCandidate> candidates, Function<CanonicalEntityCandidate, String> mapper) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (CanonicalEntityCandidate candidate : candidates) {
            String mapped = mapper.apply(candidate);
            if (StringUtils.hasText(mapped)) {
                values.add(mapped);
            }
        }
        return dedupe(values);
    }

    boolean looksLikeProperNounPhrase(String rawText, int maxWords) {
        if (!StringUtils.hasText(rawText) || maxWords <= 0) {
            return false;
        }
        String[] tokens = rawText.trim().split("\\s+");
        if (tokens.length == 0 || tokens.length > maxWords) {
            return false;
        }
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            int firstCodePoint = token.codePointAt(0);
            if (Character.isUpperCase(firstCodePoint)) {
                return true;
            }
            if (token.length() > 1 && token.equals(token.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return tokens.length == 1 && tokens[0].length() > 2 && !tokens[0].equals(tokens[0].toLowerCase(Locale.ROOT));
    }
}
