package com.deepthoughtnet.clinic.api.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.deepthoughtnet.clinic.platform.spring.context.CorrelationId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrelationIdTest {
    @Test
    void ensuresValidIncomingCorrelationIdAndFallsBackForInvalidValues() {
        assertThat(CorrelationId.ensure("corr-123")).isEqualTo("corr-123");
        assertThatCode(() -> UUID.fromString(CorrelationId.ensure(" "))).doesNotThrowAnyException();
        assertThat(CorrelationId.resolve("corr-primary", "corr-legacy")).isEqualTo("corr-primary");
        assertThat(CorrelationId.resolve(null, "corr-legacy")).isEqualTo("corr-legacy");
    }
}
