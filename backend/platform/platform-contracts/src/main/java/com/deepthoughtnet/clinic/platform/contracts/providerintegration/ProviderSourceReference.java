package com.deepthoughtnet.clinic.platform.contracts.providerintegration;

import java.io.Serializable;
import java.time.OffsetDateTime;

public record ProviderSourceReference(
        SourceSystem sourceSystem,
        String sourceEntityReference,
        long sourceRevision,
        OffsetDateTime sourceUpdatedAt
) implements Serializable {
}
