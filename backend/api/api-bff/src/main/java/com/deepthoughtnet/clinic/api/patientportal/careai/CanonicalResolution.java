package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.List;
import org.springframework.util.StringUtils;

record CanonicalResolution(
        CanonicalResolutionStatus status,
        String canonicalValue,
        List<String> candidateIds,
        List<String> clarificationOptions,
        String source,
        double confidence,
        String candidateText
) {
    CanonicalResolution {
        candidateIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
        clarificationOptions = clarificationOptions == null ? List.of() : List.copyOf(clarificationOptions);
    }

    boolean resolved() {
        return status == CanonicalResolutionStatus.RESOLVED && StringUtils.hasText(canonicalValue);
    }

    boolean ambiguous() {
        return status == CanonicalResolutionStatus.AMBIGUOUS;
    }

    static CanonicalResolution resolved(String canonicalValue,
                                        List<String> candidateIds,
                                        List<String> clarificationOptions,
                                        String source,
                                        double confidence,
                                        String candidateText) {
        return new CanonicalResolution(
                CanonicalResolutionStatus.RESOLVED,
                canonicalValue,
                candidateIds,
                clarificationOptions,
                source,
                confidence,
                candidateText
        );
    }

    static CanonicalResolution ambiguous(List<String> candidateIds,
                                         List<String> clarificationOptions,
                                         String source,
                                         double confidence,
                                         String candidateText) {
        return new CanonicalResolution(
                CanonicalResolutionStatus.AMBIGUOUS,
                null,
                candidateIds,
                clarificationOptions,
                source,
                confidence,
                candidateText
        );
    }

    static CanonicalResolution unresolved(List<String> clarificationOptions,
                                          String source,
                                          double confidence,
                                          String candidateText) {
        return new CanonicalResolution(
                CanonicalResolutionStatus.UNRESOLVED,
                null,
                List.of(),
                clarificationOptions,
                source,
                confidence,
                candidateText
        );
    }
}
