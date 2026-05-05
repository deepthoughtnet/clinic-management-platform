package com.deepthoughtnet.clinic.platform.spring.context;

import java.util.UUID;

public final class CorrelationId {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {}

    public static String ensure(String incoming) {
        if (incoming != null && !incoming.isBlank()) return incoming.trim();
        return UUID.randomUUID().toString();
    }
}
