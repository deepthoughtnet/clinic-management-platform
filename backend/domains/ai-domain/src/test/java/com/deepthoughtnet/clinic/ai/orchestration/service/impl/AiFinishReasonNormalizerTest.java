package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiFinishReasonNormalizer;
import org.junit.jupiter.api.Test;

class AiFinishReasonNormalizerTest {
    @Test
    void normalizesCommonProviderFinishReasons() {
        assertThat(AiFinishReasonNormalizer.normalize("STOP")).isEqualTo("COMPLETE");
        assertThat(AiFinishReasonNormalizer.normalize("stop")).isEqualTo("COMPLETE");
        assertThat(AiFinishReasonNormalizer.normalize("MAX_TOKENS")).isEqualTo("TRUNCATED");
        assertThat(AiFinishReasonNormalizer.normalize("length")).isEqualTo("TRUNCATED");
        assertThat(AiFinishReasonNormalizer.normalize("max_tokens")).isEqualTo("TRUNCATED");
        assertThat(AiFinishReasonNormalizer.normalize("SAFETY")).isEqualTo("BLOCKED");
        assertThat(AiFinishReasonNormalizer.normalize("content_filter")).isEqualTo("BLOCKED");
        assertThat(AiFinishReasonNormalizer.normalize("ERROR")).isEqualTo("FAILED");
        assertThat(AiFinishReasonNormalizer.normalize("UNKNOWN")).isEqualTo("UNKNOWN");
    }
}
