package com.deepthoughtnet.clinic.llm.spi;

public interface LlmClient {

    String providerName();

    default boolean isAvailable() {
        return true;
    }

    default String availabilityDiagnostic() {
        return null;
    }

    LlmResponse generate(LlmRequest request);
}
