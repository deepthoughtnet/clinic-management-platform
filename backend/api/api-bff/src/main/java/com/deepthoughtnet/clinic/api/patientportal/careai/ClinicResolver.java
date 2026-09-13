package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.util.StringUtils;

final class ClinicResolver {
    private static final Set<String> CLINIC_PREFIXES = Set.of("clinic", "hospital", "centre", "center", "branch");

    private final PatientPortalCareAiEntityRegistry entityRegistry;
    private final SelectionResolver selectionResolver = new SelectionResolver();
    private final CanonicalResolverSupport support = new CanonicalResolverSupport();

    ClinicResolver(PatientPortalCareAiEntityRegistry entityRegistry) {
        this.entityRegistry = entityRegistry;
    }

    CanonicalResolution resolve(String rawText, List<CanonicalEntityCandidate> candidates, String preferredCandidateId) {
        return selectionResolver.resolve(rawText, candidates, preferredCandidateId, this::normalize);
    }

    Optional<String> extractCandidate(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return Optional.empty();
        }
        String normalized = normalize(rawText);
        if (!StringUtils.hasText(normalized)) {
            return Optional.empty();
        }
        if (hasAlias(normalized)) {
            return Optional.of(support.collapseSpaces(normalized));
        }
        if (support.looksLikeProperNounPhrase(rawText, 6) && normalized.split("\\s+").length >= 2) {
            return Optional.of(support.collapseSpaces(normalized));
        }
        return Optional.empty();
    }

    List<String> aliases() {
        return aliasStream().distinct().toList();
    }

    private Stream<String> aliasStream() {
        Stream<String> registryAliases = entityRegistry.definitionFor(PatientPortalCareAiEntityType.CLINIC) == null
                ? Stream.empty()
                : entityRegistry.definitionFor(PatientPortalCareAiEntityType.CLINIC).aliases().stream();
        return Stream.concat(registryAliases, Stream.of("clinic", "hospital", "centre", "center", "branch"));
    }

    private boolean hasAlias(String normalized) {
        return aliasStream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .anyMatch(alias -> support.containsPhrase(" " + normalized + " ", " " + alias + " ")
                        || support.containsPhrase(" " + alias + " ", " " + normalized + " "));
    }

    private String normalize(String value) {
        String normalized = support.normalizeClinic(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.replaceAll("\\s{2,}", " ").trim();
    }
}
