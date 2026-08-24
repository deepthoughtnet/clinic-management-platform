package com.deepthoughtnet.clinic.laboratory.service.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LaboratoryReportArtifactView(
        UUID id,
        UUID tenantId,
        UUID labOrderId,
        int artifactNumber,
        String reportMode,
        String reportType,
        String reportStatus,
        String filename,
        String storageReference,
        String verificationToken,
        String verificationUrl,
        List<String> deliveryChannels,
        List<UUID> selectedItemIds,
        OffsetDateTime generatedAt,
        UUID generatedBy,
        OffsetDateTime publishedAt,
        UUID publishedBy,
        UUID supersededByArtifactId,
        OffsetDateTime supersededAt,
        String notes
) {
}
