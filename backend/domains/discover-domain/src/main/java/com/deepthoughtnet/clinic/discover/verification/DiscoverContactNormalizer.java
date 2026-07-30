package com.deepthoughtnet.clinic.discover.verification;

import java.util.Locale;
import org.springframework.util.StringUtils;

public final class DiscoverContactNormalizer {
    private DiscoverContactNormalizer() {
    }

    public static String normalizeRecipient(String value, VerificationChannel channel) {
        if (!StringUtils.hasText(value) || channel == null) {
            return null;
        }
        String trimmed = value.trim();
        if (channel == VerificationChannel.EMAIL) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        return digits;
    }
}
