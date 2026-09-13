package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class SpecialtyResolver {
    private static final Pattern NON_WORDS = Pattern.compile("[^\\p{L}\\p{N}\\p{M}]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final List<String> SEARCH_CONTEXT_WORDS = List.of(
            "book",
            "booking",
            "find",
            "show",
            "need",
            "want",
            "see",
            "doctor",
            "speciality",
            "specialty",
            "department",
            "dept",
            "consult",
            "consultation",
            "chahiye",
            "चाहिए",
            "dikh",
            "look",
            "visit"
    );
    private static final Map<String, String> CANONICAL_ALIASES = Map.ofEntries(
            Map.entry("general medicine", "General Medicine"),
            Map.entry("general physician", "General Medicine"),
            Map.entry("physician", "General Medicine"),
            Map.entry("gp", "General Medicine"),
            Map.entry("family physician", "General Medicine"),
            Map.entry("general practitioner", "General Medicine"),
            Map.entry("जनरल फिजिशियन", "General Medicine"),
            Map.entry("जनरल मेडिसिन", "General Medicine")
    );

    private final PatientPortalCareAiEntityRegistry entityRegistry;

    SpecialtyResolver(PatientPortalCareAiEntityRegistry entityRegistry) {
        this.entityRegistry = entityRegistry;
    }

    SpecialtyResolution resolve(String rawText, Collection<String> supportedSpecialties) {
        String normalizedRaw = normalize(rawText);
        if (!StringUtils.hasText(normalizedRaw)) {
            return SpecialtyResolution.unresolved(rawText, normalizedRaw, null, List.of(), "empty");
        }
        Map<String, String> supportedIndex = supportedSpecialties == null ? Map.of() : canonicalIndex(supportedSpecialties);
        if (supportedIndex.isEmpty()) {
            return resolutionFromAliasScan(rawText, normalizedRaw, supportedIndex, List.of());
        }

        String exactSupported = supportedIndex.get(normalizedRaw);
        if (StringUtils.hasText(exactSupported)) {
            return SpecialtyResolution.resolved(rawText, normalizedRaw, exactSupported, exactSupported, 0.99d, "exact-canonical");
        }

        List<String> aliasCandidates = aliasCandidates(rawText, normalizedRaw);
        List<String> supportedMatches = new ArrayList<>();
        for (String aliasCandidate : aliasCandidates) {
            String canonicalAlias = CANONICAL_ALIASES.get(normalize(aliasCandidate));
            if (!StringUtils.hasText(canonicalAlias)) {
                continue;
            }
            String supported = supportedIndex.get(normalize(canonicalAlias));
            if (StringUtils.hasText(supported) && !supportedMatches.contains(supported)) {
                supportedMatches.add(supported);
            }
        }
        if (supportedMatches.size() == 1) {
            String supported = supportedMatches.getFirst();
            return SpecialtyResolution.resolved(
                    rawText,
                    normalizedRaw,
                    supported,
                    aliasCandidates.isEmpty() ? supported : aliasCandidates.getFirst(),
                    0.95d,
                    "deterministic-alias"
            );
        }
        if (supportedMatches.size() > 1) {
            return SpecialtyResolution.ambiguous(rawText, normalizedRaw, supportedMatches, "ambiguous-alias");
        }

        for (String supported : supportedIndex.values()) {
            String normalizedSupported = normalize(supported);
            if (containsPhrase(normalizedRaw, normalizedSupported) || containsPhrase(normalizedSupported, normalizedRaw)) {
                supportedMatches.add(supported);
            }
        }
        supportedMatches = dedupeInOrder(supportedMatches);
        if (supportedMatches.size() == 1) {
            String supported = supportedMatches.getFirst();
            return SpecialtyResolution.resolved(rawText, normalizedRaw, supported, supported, 0.9d, "catalog-match");
        }
        if (supportedMatches.size() > 1) {
            return SpecialtyResolution.ambiguous(rawText, normalizedRaw, supportedMatches, "catalog-ambiguous");
        }

        if (StringUtils.hasText(normalizedRaw)) {
            List<String> aliasOnlyMatches = aliasCandidates(rawText, normalizedRaw);
            if (aliasOnlyMatches.size() == 1) {
                String alias = aliasOnlyMatches.getFirst();
                return SpecialtyResolution.unresolved(rawText, normalizedRaw, alias, List.of(alias), "unsupported-alias");
            }
            if (aliasOnlyMatches.size() > 1) {
                return SpecialtyResolution.ambiguous(rawText, normalizedRaw, aliasOnlyMatches, "alias-ambiguous");
            }
        }
        return SpecialtyResolution.unresolved(rawText, normalizedRaw, null, List.of(), "no-match");
    }

    Optional<String> extractCandidate(String rawText) {
        String normalizedRaw = normalize(rawText);
        if (!StringUtils.hasText(normalizedRaw)) {
            return Optional.empty();
        }
        List<String> candidates = aliasCandidates(rawText, normalizedRaw);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.getFirst());
        }
        return Optional.empty();
    }

    List<String> canonicalAliases() {
        return new ArrayList<>(CANONICAL_ALIASES.keySet());
    }

    private SpecialtyResolution resolutionFromAliasScan(String rawText,
                                                        String normalizedRaw,
                                                        Map<String, String> supportedIndex,
                                                        List<String> fallbackCandidates) {
        List<String> aliasCandidates = aliasCandidates(rawText, normalizedRaw);
        if (aliasCandidates.isEmpty()) {
            return SpecialtyResolution.unresolved(rawText, normalizedRaw, null, fallbackCandidates, "no-supported-specialties");
        }
        List<String> supportedMatches = new ArrayList<>();
        for (String aliasCandidate : aliasCandidates) {
            String canonicalAlias = CANONICAL_ALIASES.get(normalize(aliasCandidate));
            if (!StringUtils.hasText(canonicalAlias)) {
                continue;
            }
            String supported = supportedIndex.get(normalize(canonicalAlias));
            if (StringUtils.hasText(supported) && !supportedMatches.contains(supported)) {
                supportedMatches.add(supported);
            }
        }
        if (supportedMatches.size() == 1) {
            String supported = supportedMatches.getFirst();
            return SpecialtyResolution.resolved(rawText, normalizedRaw, supported, aliasCandidates.getFirst(), 0.95d, "deterministic-alias");
        }
        if (supportedMatches.size() > 1) {
            return SpecialtyResolution.ambiguous(rawText, normalizedRaw, supportedMatches, "ambiguous-alias");
        }
        return SpecialtyResolution.unresolved(rawText, normalizedRaw, aliasCandidates.getFirst(), aliasCandidates, "unsupported-alias");
    }

    private List<String> aliasCandidates(String rawText, String normalizedRaw) {
        if (!StringUtils.hasText(normalizedRaw)) {
            return List.of();
        }
        List<String> aliases = new ArrayList<>();
        if (entityRegistry.definitionFor(PatientPortalCareAiEntityType.SPECIALITY) != null) {
            aliases.addAll(entityRegistry.definitionFor(PatientPortalCareAiEntityType.SPECIALITY).aliases());
        }
        aliases.addAll(canonicalAliases());
        aliases = aliases.stream()
                .map(this::normalizeAliasKey)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        List<String> matches = new ArrayList<>();
        for (String alias : aliases) {
            if (containsPhrase(normalizedRaw, alias) && qualifiesAsSpecialtyPhrase(normalizedRaw, alias)) {
                matches.add(displayAlias(alias));
            }
        }
        if (matches.isEmpty() && isShortSpecialtyLikePhrase(normalizedRaw)) {
            matches.add(normalizedRaw);
        }
        return dedupeInOrder(matches);
    }

    private boolean qualifiesAsSpecialtyPhrase(String normalizedRaw, String alias) {
        if (!StringUtils.hasText(normalizedRaw) || !StringUtils.hasText(alias)) {
            return false;
        }
        if (normalizedRaw.equals(alias)) {
            return true;
        }
        int wordCount = normalizedRaw.split("\\s+").length;
        if (wordCount <= 4) {
            return true;
        }
        return SEARCH_CONTEXT_WORDS.stream().anyMatch(word -> containsPhrase(normalizedRaw, word));
    }

    private Map<String, String> canonicalIndex(Collection<String> specialties) {
        Map<String, String> index = new LinkedHashMap<>();
        for (String specialty : specialties) {
            String normalized = normalize(specialty);
            if (StringUtils.hasText(normalized) && !index.containsKey(normalized)) {
                index.put(normalized, specialty);
            }
        }
        return index;
    }

    private List<String> dedupeInOrder(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private String displayAlias(String alias) {
        String canonical = CANONICAL_ALIASES.get(alias);
        return StringUtils.hasText(canonical) ? canonical : alias;
    }

    private String normalizeAliasKey(String value) {
        return normalize(value);
    }

    private boolean containsPhrase(String text, String phrase) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(phrase)) {
            return false;
        }
        String normalizedText = " " + text + " ";
        String normalizedPhrase = " " + phrase + " ";
        return normalizedText.contains(normalizedPhrase);
    }

    private boolean isShortSpecialtyLikePhrase(String normalizedRaw) {
        if (!StringUtils.hasText(normalizedRaw)) {
            return false;
        }
        String[] words = normalizedRaw.split("\\s+");
        if (words.length == 0 || words.length > 2) {
            return false;
        }
        String lastWord = words[words.length - 1];
        return "medicine".equals(lastWord)
                || "physician".equals(lastWord)
                || "practitioner".equals(lastWord)
                || "gp".equals(lastWord);
    }

    private String normalize(String value) {
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

    record SpecialtyResolution(
            String rawText,
            String normalizedText,
            String canonicalSpecialty,
            String candidateText,
            double confidence,
            SpecialtyResolutionStatus status,
            List<String> candidates,
            String reason
    ) {
        static SpecialtyResolution resolved(String rawText,
                                            String normalizedText,
                                            String canonicalSpecialty,
                                            String candidateText,
                                            double confidence,
                                            String reason) {
            return new SpecialtyResolution(
                    rawText,
                    normalizedText,
                    canonicalSpecialty,
                    candidateText,
                    confidence,
                    SpecialtyResolutionStatus.RESOLVED,
                    canonicalSpecialty == null ? List.of() : List.of(canonicalSpecialty),
                    reason
            );
        }

        static SpecialtyResolution ambiguous(String rawText,
                                             String normalizedText,
                                             List<String> candidates,
                                             String reason) {
            return new SpecialtyResolution(
                    rawText,
                    normalizedText,
                    null,
                    null,
                    0.5d,
                    SpecialtyResolutionStatus.AMBIGUOUS,
                    candidates == null ? List.of() : List.copyOf(candidates),
                    reason
            );
        }

        static SpecialtyResolution unresolved(String rawText,
                                              String normalizedText,
                                              String candidateText,
                                              List<String> candidates,
                                              String reason) {
            return new SpecialtyResolution(
                    rawText,
                    normalizedText,
                    null,
                    candidateText,
                    0.0d,
                    SpecialtyResolutionStatus.UNRESOLVED,
                    candidates == null ? List.of() : List.copyOf(candidates),
                    reason
            );
        }

        boolean resolved() {
            return status == SpecialtyResolutionStatus.RESOLVED && StringUtils.hasText(canonicalSpecialty);
        }
    }

    enum SpecialtyResolutionStatus {
        RESOLVED,
        AMBIGUOUS,
        UNRESOLVED
    }
}
