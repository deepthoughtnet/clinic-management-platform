package com.deepthoughtnet.clinic.llm.spi;

public interface LlmClient {

    String providerName();

    LlmResponse generate(LlmRequest request);
}