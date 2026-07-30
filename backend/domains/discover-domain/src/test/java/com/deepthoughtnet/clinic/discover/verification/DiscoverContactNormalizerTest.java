package com.deepthoughtnet.clinic.discover.verification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscoverContactNormalizerTest {
    @Test
    void normalizesIndianMobileFormatsForSmsLogin() {
        assertThat(DiscoverContactNormalizer.normalizeRecipient("9876501201", VerificationChannel.SMS)).isEqualTo("9876501201");
        assertThat(DiscoverContactNormalizer.normalizeRecipient("+919876501201", VerificationChannel.SMS)).isEqualTo("9876501201");
        assertThat(DiscoverContactNormalizer.normalizeRecipient("+91 98765 01201", VerificationChannel.SMS)).isEqualTo("9876501201");
    }

    @Test
    void lowerCasesEmailRecipients() {
        assertThat(DiscoverContactNormalizer.normalizeRecipient("Discover.Clinic.UAT@Jeevanam.Test", VerificationChannel.EMAIL))
                .isEqualTo("discover.clinic.uat@jeevanam.test");
    }
}
