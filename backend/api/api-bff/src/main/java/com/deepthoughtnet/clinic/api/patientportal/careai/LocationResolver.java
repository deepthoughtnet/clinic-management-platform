package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.List;
import java.util.Optional;
import org.springframework.util.StringUtils;

final class LocationResolver {
    private final PatientPortalCareAiEntityRegistry entityRegistry;
    private final SelectionResolver selectionResolver = new SelectionResolver();
    private final CanonicalResolverSupport support = new CanonicalResolverSupport();

    LocationResolver(PatientPortalCareAiEntityRegistry entityRegistry) {
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
        return Optional.empty();
    }

    List<String> aliases() {
        return entityRegistry.definitionFor(PatientPortalCareAiEntityType.LOCATION) == null
                ? List.of()
                : java.util.stream.Stream.concat(
                                entityRegistry.definitionFor(PatientPortalCareAiEntityType.LOCATION).exampleUtterances().stream(),
                                entityRegistry.definitionFor(PatientPortalCareAiEntityType.LOCATION).aliases().stream())
                        .distinct()
                        .toList();
    }

    private boolean hasAlias(String normalized) {
        return aliases().stream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .anyMatch(alias -> support.containsPhrase(" " + normalized + " ", " " + alias + " ")
                        || support.containsPhrase(" " + alias + " ", " " + normalized + " "));
    }

    private String normalize(String value) {
        String normalized = support.normalizeLocation(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }
}
