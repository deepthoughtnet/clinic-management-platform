package com.deepthoughtnet.clinic.platform.spring.context;

import java.util.regex.Pattern;
import java.util.UUID;

public final class CorrelationId {
    public static final String HEADER = "X-Correlation-ID";
    public static final String LEGACY_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private CorrelationId() {}

    public static String ensure(String incoming) {
        if (incoming != null && !incoming.isBlank()) {
            String trimmed = incoming.trim();
            if (SAFE.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }

    public static String resolve(String primary, String legacy) {
        if (primary != null && !primary.isBlank()) {
            return ensure(primary);
        }
        return ensure(legacy);
    }
}
