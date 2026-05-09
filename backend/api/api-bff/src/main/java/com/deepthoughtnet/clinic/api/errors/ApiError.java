package com.deepthoughtnet.clinic.api.errors;

import java.time.OffsetDateTime;

public record ApiError(
        OffsetDateTime timestamp,
        String path,
        int status,
        String code,
        String message,
        String correlationId,
        String requestId
) {
    public static ApiError of(int status, String code, String message, String path, String correlationId) {
        return new ApiError(OffsetDateTime.now(), path, status, code, message, correlationId, correlationId);
    }
}
