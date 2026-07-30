package com.deepthoughtnet.clinic.api.errors;

import java.time.OffsetDateTime;

public record ValidationErrorResponse(
        OffsetDateTime timestamp,
        String path,
        int status,
        String code,
        String field,
        String message,
        String correlationId,
        String requestId
) {
    public static ValidationErrorResponse of(int status, String code, String field, String message, String path, String correlationId) {
        return new ValidationErrorResponse(OffsetDateTime.now(), path, status, code, field, message, correlationId, correlationId);
    }
}
