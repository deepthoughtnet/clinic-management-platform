package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "pharmacy_reconciliations",
        indexes = {
                @Index(name = "ix_pharmacy_reconciliations_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "ix_pharmacy_reconciliations_tenant_created", columnList = "tenant_id,created_at")
        }
)
public class PharmacyReconciliationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "medicine_id")
    private UUID medicineId;

    @Column(name = "stock_batch_id")
    private UUID stockBatchId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "system_quantity", nullable = false)
    private int systemQuantity;

    @Column(name = "physical_quantity")
    private Integer physicalQuantity;

    @Column(name = "variance_quantity")
    private Integer varianceQuantity;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "sheet_document_id")
    private UUID sheetDocumentId;

    @Column(name = "sheet_filename", length = 256)
    private String sheetFilename;

    @Column(name = "sheet_media_type", length = 128)
    private String sheetMediaType;

    @Column(name = "sheet_storage_key", length = 512)
    private String sheetStorageKey;

    @Column(name = "extraction_status", length = 24)
    private String extractionStatus;

    @Column(name = "extraction_provider", length = 64)
    private String extractionProvider;

    @Column(name = "extraction_confidence", precision = 5, scale = 2)
    private java.math.BigDecimal extractionConfidence;

    @Column(name = "extracted_rows_json", columnDefinition = "text")
    private String extractedRowsJson;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "adjusted_by")
    private UUID adjustedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected PharmacyReconciliationEntity() {}

    public static PharmacyReconciliationEntity create(UUID tenantId, UUID medicineId, UUID stockBatchId, UUID supplierId, UUID locationId, int systemQuantity, UUID createdBy) {
        OffsetDateTime now = OffsetDateTime.now();
        PharmacyReconciliationEntity entity = new PharmacyReconciliationEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.medicineId = medicineId;
        entity.stockBatchId = stockBatchId;
        entity.supplierId = supplierId;
        entity.locationId = locationId;
        entity.systemQuantity = systemQuantity;
        entity.status = "PENDING";
        entity.createdBy = createdBy;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void attachSheet(UUID sheetDocumentId, String sheetFilename, String sheetMediaType, String sheetStorageKey, String extractionStatus, String extractionProvider, java.math.BigDecimal extractionConfidence, String extractedRowsJson) {
        this.sheetDocumentId = sheetDocumentId;
        this.sheetFilename = sheetFilename;
        this.sheetMediaType = sheetMediaType;
        this.sheetStorageKey = sheetStorageKey;
        this.extractionStatus = extractionStatus;
        this.extractionProvider = extractionProvider;
        this.extractionConfidence = extractionConfidence;
        this.extractedRowsJson = extractedRowsJson;
        this.updatedAt = OffsetDateTime.now();
    }

    public void captureCount(Integer physicalQuantity, int varianceQuantity, String reason) {
        this.physicalQuantity = physicalQuantity;
        this.varianceQuantity = varianceQuantity;
        this.reason = reason;
        this.status = "PENDING";
        this.updatedAt = OffsetDateTime.now();
    }

    public void reviewExtraction(String extractedRowsJson) {
        this.extractedRowsJson = extractedRowsJson;
        this.extractionStatus = "REVIEWED";
        this.updatedAt = OffsetDateTime.now();
        this.reviewedAt = this.updatedAt;
    }

    public void applyExtraction() {
        this.status = "APPLIED";
        this.extractionStatus = "APPLIED";
        this.updatedAt = OffsetDateTime.now();
        this.appliedAt = this.updatedAt;
    }

    public void confirm(Integer physicalQuantity, int varianceQuantity, String reason, UUID adjustedBy, boolean adjustStock) {
        this.physicalQuantity = physicalQuantity;
        this.varianceQuantity = varianceQuantity;
        this.reason = reason;
        this.status = adjustStock ? "CONFIRMED" : "REVIEWED";
        this.adjustedBy = adjustedBy;
        this.confirmedAt = OffsetDateTime.now();
        this.updatedAt = this.confirmedAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getMedicineId() { return medicineId; }
    public UUID getStockBatchId() { return stockBatchId; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getLocationId() { return locationId; }
    public int getSystemQuantity() { return systemQuantity; }
    public Integer getPhysicalQuantity() { return physicalQuantity; }
    public Integer getVarianceQuantity() { return varianceQuantity; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public UUID getSheetDocumentId() { return sheetDocumentId; }
    public String getSheetFilename() { return sheetFilename; }
    public String getSheetMediaType() { return sheetMediaType; }
    public String getSheetStorageKey() { return sheetStorageKey; }
    public String getExtractionStatus() { return extractionStatus; }
    public String getExtractionProvider() { return extractionProvider; }
    public java.math.BigDecimal getExtractionConfidence() { return extractionConfidence; }
    public String getExtractedRowsJson() { return extractedRowsJson; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getAdjustedBy() { return adjustedBy; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public OffsetDateTime getAppliedAt() { return appliedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
