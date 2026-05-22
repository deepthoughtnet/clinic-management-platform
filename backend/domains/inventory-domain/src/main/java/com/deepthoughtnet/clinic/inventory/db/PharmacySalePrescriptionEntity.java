package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "pharmacy_sale_prescriptions",
        indexes = {
                @Index(name = "ix_pharmacy_sale_prescriptions_tenant_created", columnList = "tenant_id,created_at"),
                @Index(name = "ix_pharmacy_sale_prescriptions_tenant_sale", columnList = "tenant_id,linked_sale_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_pharmacy_sale_prescriptions_storage_key", columnNames = {"tenant_id", "storage_key"})
        }
)
public class PharmacySalePrescriptionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "linked_sale_id")
    private UUID linkedSaleId;

    @Column(name = "uploaded_by_app_user_id", nullable = false)
    private UUID uploadedByAppUserId;

    @Column(name = "original_filename", nullable = false, length = 512)
    private String originalFilename;

    @Column(name = "media_type", nullable = false, length = 128)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "storage_key", nullable = false, length = 1024)
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PharmacySalePrescriptionEntity() {
    }

    public static PharmacySalePrescriptionEntity create(
            UUID tenantId,
            UUID uploadedByAppUserId,
            String originalFilename,
            String mediaType,
            long sizeBytes,
            String checksumSha256,
            String storageKey
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        PharmacySalePrescriptionEntity entity = new PharmacySalePrescriptionEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.linkedSaleId = null;
        entity.uploadedByAppUserId = uploadedByAppUserId;
        entity.originalFilename = originalFilename;
        entity.mediaType = mediaType;
        entity.sizeBytes = sizeBytes;
        entity.checksumSha256 = checksumSha256;
        entity.storageKey = storageKey;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void linkToSale(UUID saleId) {
        this.linkedSaleId = saleId;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getLinkedSaleId() { return linkedSaleId; }
    public UUID getUploadedByAppUserId() { return uploadedByAppUserId; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMediaType() { return mediaType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public String getStorageKey() { return storageKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
