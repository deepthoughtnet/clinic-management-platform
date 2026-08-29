package com.deepthoughtnet.clinic.inventory.service.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record PhysicalCountSessionSaveCommand(
        @NotBlank
        @Size(max = 256)
        String sessionName,
        @NotNull
        UUID locationId,
        @NotBlank
        @Size(max = 256)
        String locationName,
        @NotBlank
        @Size(max = 32)
        String scope,
        @NotBlank
        @Size(max = 128)
        String scopeLabel,
        @NotBlank
        @Size(max = 64)
        String reason,
        @NotBlank
        @Size(max = 24)
        String status,
        @NotEmpty
        @Valid
        List<PhysicalCountSessionLine> lines,
        @NotNull
        @Valid
        PhysicalCountAuditFields audit
) {
}
