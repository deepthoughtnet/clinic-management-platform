package com.deepthoughtnet.clinic.api.lab.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "lab_order_attachments",
        indexes = {
                @Index(name = "ix_lab_order_attachments_tenant_order", columnList = "tenant_id,lab_order_id,created_at")
        }
)
public class LabOrderAttachmentEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lab_order_id", nullable = false)
    private UUID labOrderId;

    @Column(name = "attachment_type", nullable = false, length = 32)
    private String attachmentType;

    @Column(name = "original_filename", nullable = false, length = 256)
    private String originalFilename;

    @Column(name = "media_type", nullable = false, length = 128)
    private String mediaType;

    @Column(name = "storage_key", length = 512)
    private String storageKey;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum_sha256", length = 128)
    private String checksumSha256;

    @Column(name = "dicom_metadata_json", columnDefinition = "text")
    private String dicomMetadataJson;

    @Column(name = "uploaded_by_user_id")
    private UUID uploadedByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected LabOrderAttachmentEntity() {
    }

    public static LabOrderAttachmentEntity create(
            UUID tenantId,
            UUID labOrderId,
            String attachmentType,
            String originalFilename,
            String mediaType,
            String storageKey,
            Long sizeBytes,
            String checksumSha256,
            String dicomMetadataJson,
            UUID uploadedByUserId
    ) {
        LabOrderAttachmentEntity entity = new LabOrderAttachmentEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.labOrderId = labOrderId;
        entity.attachmentType = attachmentType;
        entity.originalFilename = originalFilename;
        entity.mediaType = mediaType;
        entity.storageKey = storageKey;
        entity.sizeBytes = sizeBytes;
        entity.checksumSha256 = checksumSha256;
        entity.dicomMetadataJson = dicomMetadataJson;
        entity.uploadedByUserId = uploadedByUserId;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getLabOrderId() { return labOrderId; }
    public String getAttachmentType() { return attachmentType; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMediaType() { return mediaType; }
    public String getStorageKey() { return storageKey; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public String getDicomMetadataJson() { return dicomMetadataJson; }
    public UUID getUploadedByUserId() { return uploadedByUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
