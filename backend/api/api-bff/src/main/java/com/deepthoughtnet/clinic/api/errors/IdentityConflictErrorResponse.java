package com.deepthoughtnet.clinic.api.errors;

import java.time.OffsetDateTime;
import java.util.List;

public record IdentityConflictErrorResponse(
        OffsetDateTime timestamp,
        String path,
        int status,
        String code,
        String message,
        List<IdentityConflictItem> conflicts,
        String correlationId,
        String requestId
) {
    public static IdentityConflictErrorResponse of(
            int status,
            String code,
            String message,
            String path,
            String correlationId,
            List<IdentityConflictItem> conflicts
    ) {
        return new IdentityConflictErrorResponse(
                OffsetDateTime.now(),
                path,
                status,
                code,
                message,
                conflicts == null ? List.of() : List.copyOf(conflicts),
                correlationId,
                correlationId
        );
    }

    public record IdentityConflictItem(String field, String code, String message) {
    }
}
