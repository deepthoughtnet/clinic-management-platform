package com.deepthoughtnet.clinic.platform.contracts.providerintegration;

import java.io.Serializable;
import java.time.OffsetDateTime;

public record ProviderMatchEvidence(
        String evidenceType,
        String result,
        EvidenceStrength strength,
        String publicDisplayValue,
        String platformDisplayValue,
        long sourceRevision,
        OffsetDateTime recordedAt,
        String explanation
) implements Serializable {
}
