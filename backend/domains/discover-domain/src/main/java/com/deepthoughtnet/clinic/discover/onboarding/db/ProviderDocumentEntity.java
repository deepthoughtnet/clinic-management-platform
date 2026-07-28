package com.deepthoughtnet.clinic.discover.onboarding.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_provider_documents")
public class ProviderDocumentEntity {
    @Id
    private UUID id;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 64)
    private ProviderDocumentType documentType;
    @Column(name = "original_filename", nullable = false, length = 256)
    private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;
    @Column(name = "virus_scan_status", nullable = false, length = 32)
    private String virusScanStatus;
    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    protected ProviderDocumentEntity() {
    }

    public ProviderDocumentEntity(UUID providerId, ProviderDocumentType documentType, String originalFilename, String contentType, long sizeBytes, String storageKey) {
        this.id = UUID.randomUUID();
        this.providerId = providerId;
        this.documentType = documentType;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.virusScanStatus = "PENDING";
        this.uploadedAt = OffsetDateTime.now();
    }
    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public ProviderDocumentType getDocumentType() { return documentType; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public String getVirusScanStatus() { return virusScanStatus; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
}
