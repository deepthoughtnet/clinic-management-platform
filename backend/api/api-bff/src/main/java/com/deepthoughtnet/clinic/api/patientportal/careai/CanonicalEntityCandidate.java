package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.List;
import org.springframework.util.StringUtils;

record CanonicalEntityCandidate(
        String candidateId,
        String canonicalValue,
        String label,
        List<String> aliases
) {
    CanonicalEntityCandidate {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    String displayLabel() {
        if (StringUtils.hasText(label)) {
            return label;
        }
        return StringUtils.hasText(canonicalValue) ? canonicalValue : candidateId;
    }
}
