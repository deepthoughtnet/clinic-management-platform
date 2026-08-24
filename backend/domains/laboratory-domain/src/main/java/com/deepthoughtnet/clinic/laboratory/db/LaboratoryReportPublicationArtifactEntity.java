package com.deepthoughtnet.clinic.laboratory.db;

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
        name = "lab_report_publication_artifacts",
        indexes = {
                @Index(name = "ix_lab_report_publication_artifacts_order", columnList = "tenant_id,lab_order_id,published_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lab_report_publication_artifact", columnNames = {"tenant_id", "lab_order_id", "artifact_number"})
        }
)
public class LaboratoryReportPublicationArtifactEntity {
    public static final String REPORT_MODE_INDIVIDUAL = "INDIVIDUAL";
    public static final String REPORT_MODE_GROUPED = "GROUPED";
    public static final String REPORT_MODE_CONSOLIDATED = "CONSOLIDATED";
    public static final String REPORT_TYPE_INTERIM = "INTERIM";
    public static final String REPORT_TYPE_FINAL = "FINAL";
    public static final String REPORT_STATUS_CURRENT = "CURRENT";
    public static final String REPORT_STATUS_HISTORICAL = "HISTORICAL";
    public static final String REPORT_STATUS_SUPERSEDED = "SUPERSEDED";

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lab_order_id", nullable = false)
    private UUID labOrderId;

    @Column(name = "artifact_number", nullable = false)
    private int artifactNumber;

    @Column(name = "report_mode", nullable = false, length = 24)
    private String reportMode;

    @Column(name = "report_type", nullable = false, length = 24)
    private String reportType;

    @Column(name = "report_status", nullable = false, length = 24)
    private String reportStatus;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "generated_by")
    private UUID generatedBy;

    @Column(nullable = false, length = 512)
    private String filename;

    @Column(name = "storage_reference", length = 1024)
    private String storageReference;

    @Column(name = "verification_token", length = 64)
    private String verificationToken;

    @Column(name = "verification_url", length = 1024)
    private String verificationUrl;

    @Column(name = "delivery_channels", columnDefinition = "text")
    private String deliveryChannels;

    @Column(name = "selected_item_ids", columnDefinition = "text")
    private String selectedItemIds;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "superseded_by_artifact_id")
    private UUID supersededByArtifactId;

    @Column(name = "superseded_at")
    private OffsetDateTime supersededAt;

    @Column(columnDefinition = "text")
    private String notes;

    protected LaboratoryReportPublicationArtifactEntity() {
    }

    public static LaboratoryReportPublicationArtifactEntity create(
            UUID tenantId,
            UUID labOrderId,
            int artifactNumber,
            String filename,
            String storageReference,
            String verificationToken,
            String verificationUrl,
            String deliveryChannels,
            String selectedItemIds,
            String reportMode,
            String reportType,
            String reportStatus,
            OffsetDateTime generatedAt,
            UUID generatedBy,
            String notes,
            UUID publishedBy
    ) {
        LaboratoryReportPublicationArtifactEntity entity = new LaboratoryReportPublicationArtifactEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.labOrderId = labOrderId;
        entity.artifactNumber = artifactNumber;
        entity.filename = filename;
        entity.storageReference = storageReference;
        entity.verificationToken = verificationToken;
        entity.verificationUrl = verificationUrl;
        entity.deliveryChannels = deliveryChannels;
        entity.selectedItemIds = selectedItemIds;
        entity.reportMode = normalize(reportMode, REPORT_MODE_CONSOLIDATED);
        entity.reportType = normalize(reportType, REPORT_TYPE_FINAL);
        entity.reportStatus = normalize(reportStatus, REPORT_STATUS_CURRENT);
        entity.generatedAt = generatedAt == null ? OffsetDateTime.now() : generatedAt;
        entity.generatedBy = generatedBy;
        entity.publishedAt = entity.generatedAt;
        entity.publishedBy = publishedBy;
        entity.notes = notes;
        return entity;
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getLabOrderId() {
        return labOrderId;
    }

    public int getArtifactNumber() {
        return artifactNumber;
    }

    public String getReportMode() {
        return reportMode;
    }

    public String getReportType() {
        return reportType;
    }

    public String getReportStatus() {
        return reportStatus;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public UUID getGeneratedBy() {
        return generatedBy;
    }

    public String getFilename() {
        return filename;
    }

    public String getStorageReference() {
        return storageReference;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public String getVerificationUrl() {
        return verificationUrl;
    }

    public String getDeliveryChannels() {
        return deliveryChannels;
    }

    public String getSelectedItemIds() {
        return selectedItemIds;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public UUID getSupersededByArtifactId() {
        return supersededByArtifactId;
    }

    public OffsetDateTime getSupersededAt() {
        return supersededAt;
    }

    public String getNotes() {
        return notes;
    }

    public void markSuperseded(UUID supersededByArtifactId) {
        this.reportStatus = REPORT_STATUS_SUPERSEDED;
        this.supersededByArtifactId = supersededByArtifactId;
        this.supersededAt = OffsetDateTime.now();
    }

    public void markHistorical() {
        this.reportStatus = REPORT_STATUS_HISTORICAL;
    }
}
