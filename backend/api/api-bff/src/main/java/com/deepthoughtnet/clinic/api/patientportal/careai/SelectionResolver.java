package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.util.StringUtils;

final class SelectionResolver {
    private final CanonicalResolverSupport support = new CanonicalResolverSupport();

    CanonicalResolution resolve(String rawText,
                                List<CanonicalEntityCandidate> candidates,
                                String preferredCandidateId,
                                Function<String, String> normalizer) {
        if (!StringUtils.hasText(rawText) || candidates == null || candidates.isEmpty()) {
            return CanonicalResolution.unresolved(List.of(), "no-candidates", 0.0d, rawText);
        }
        Function<String, String> safeNormalizer = normalizer == null ? support::normalize : normalizer;
        String normalizedRaw = safeNormalizer.apply(rawText);
        if (!StringUtils.hasText(normalizedRaw)) {
            return CanonicalResolution.unresolved(candidateLabels(candidates), "empty", 0.0d, rawText);
        }

        Integer ordinal = support.parseOrdinalIndex(normalizedRaw);
        if (ordinal != null) {
            int index = ordinal == Integer.MAX_VALUE ? candidates.size() - 1 : ordinal - 1;
            if (index >= 0 && index < candidates.size()) {
                CanonicalEntityCandidate selected = candidates.get(index);
                return CanonicalResolution.resolved(
                        selected.canonicalValue(),
                        List.of(selected.candidateId()),
                        candidateLabels(candidates),
                        ordinal == Integer.MAX_VALUE ? "ordinal-last" : "ordinal-selection",
                        0.98d,
                        rawText
                );
            }
        }

        if (support.isReferentialSelection(normalizedRaw) && StringUtils.hasText(preferredCandidateId)) {
            for (CanonicalEntityCandidate candidate : candidates) {
                if (preferredCandidateId.equals(candidate.candidateId())) {
                    return CanonicalResolution.resolved(
                            candidate.canonicalValue(),
                            List.of(candidate.candidateId()),
                            candidateLabels(candidates),
                            "referential-selection",
                            0.9d,
                            rawText
                    );
                }
            }
        }

        List<CanonicalEntityCandidate> exactMatches = new ArrayList<>();
        List<CanonicalEntityCandidate> partialMatches = new ArrayList<>();
        for (CanonicalEntityCandidate candidate : candidates) {
            if (matchesCandidate(candidate, normalizedRaw, safeNormalizer, true)) {
                exactMatches.add(candidate);
                continue;
            }
            if (matchesCandidate(candidate, normalizedRaw, safeNormalizer, false)) {
                partialMatches.add(candidate);
            }
        }
        if (exactMatches.size() == 1) {
            CanonicalEntityCandidate selected = exactMatches.getFirst();
            return CanonicalResolution.resolved(
                    selected.canonicalValue(),
                    List.of(selected.candidateId()),
                    candidateLabels(candidates),
                    "exact-match",
                    0.97d,
                    rawText
            );
        }
        if (exactMatches.size() > 1) {
            return CanonicalResolution.ambiguous(
                    exactCandidateIds(exactMatches),
                    candidateLabels(exactMatches),
                    "ambiguous-exact",
                    0.88d,
                    rawText
            );
        }
        if (partialMatches.size() == 1) {
            CanonicalEntityCandidate selected = partialMatches.getFirst();
            return CanonicalResolution.resolved(
                    selected.canonicalValue(),
                    List.of(selected.candidateId()),
                    candidateLabels(candidates),
                    "partial-match",
                    0.82d,
                    rawText
            );
        }
        if (partialMatches.size() > 1) {
            return CanonicalResolution.ambiguous(
                    exactCandidateIds(partialMatches),
                    candidateLabels(partialMatches),
                    "ambiguous-partial",
                    0.7d,
                    rawText
            );
        }
        return CanonicalResolution.unresolved(candidateLabels(candidates), "no-match", 0.0d, rawText);
    }

    private boolean matchesCandidate(CanonicalEntityCandidate candidate,
                                    String normalizedRaw,
                                    Function<String, String> normalizer,
                                    boolean exactOnly) {
        List<String> terms = new ArrayList<>();
        terms.add(candidate.canonicalValue());
        terms.add(candidate.label());
        terms.addAll(candidate.aliases());
        for (String term : terms) {
            String normalizedTerm = normalizer.apply(term);
            if (!StringUtils.hasText(normalizedTerm)) {
                continue;
            }
            if (normalizedRaw.equals(normalizedTerm)) {
                return true;
            }
            if (!exactOnly && matchesPartial(normalizedRaw, normalizedTerm)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPartial(String normalizedRaw, String normalizedTerm) {
        if (!StringUtils.hasText(normalizedRaw) || !StringUtils.hasText(normalizedTerm)) {
            return false;
        }
        if (normalizedTerm.startsWith(normalizedRaw) || normalizedRaw.startsWith(normalizedTerm)) {
            return true;
        }
        return support.containsPhrase(normalizedTerm, normalizedRaw) || support.containsPhrase(normalizedRaw, normalizedTerm);
    }

    private List<String> candidateLabels(List<CanonicalEntityCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(CanonicalEntityCandidate::displayLabel)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> exactCandidateIds(List<CanonicalEntityCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(CanonicalEntityCandidate::candidateId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
