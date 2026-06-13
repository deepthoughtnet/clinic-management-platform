package com.deepthoughtnet.clinic.api.lab.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LabOrderAttachmentRecord(
        UUID id,
        UUID labOrderId,
        String attachmentType,
        String originalFilename,
        String mediaType,
        String storageKey,
        Long sizeBytes,
        String checksumSha256,
        String dicomMetadataJson,
        UUID uploadedByUserId,
        OffsetDateTime createdAt
) {
}
