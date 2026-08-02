package com.deepthoughtnet.clinic.platform.contracts.providerintegration;

import java.io.Serializable;

public record PublicProviderReference(
        String publicProviderId,
        String publicPracticeId
) implements Serializable {
}
