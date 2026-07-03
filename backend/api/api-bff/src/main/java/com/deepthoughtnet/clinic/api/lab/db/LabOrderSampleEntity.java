package com.deepthoughtnet.clinic.api.lab.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "lab_order_samples",
        indexes = {
                @Index(name = "ix_lab_order_samples_tenant", columnList = "tenant_id"),
                @Index(name = "ix_lab_order_samples_order", columnList = "tenant_id,lab_order_id"),
                @Index(name = "ix_lab_order_samples_status", columnList = "tenant_id,status"),
                @Index(name = "ix_lab_order_samples_collected_at", columnList = "tenant_id,collected_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lab_order_samples_tenant_accession", columnNames = {"tenant_id", "accession_number"}),
                @UniqueConstraint(name = "uq_lab_order_samples_tenant_barcode", columnNames = {"tenant_id", "barcode_value"})
        }
)
public class LabOrderSampleEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lab_order_id", nullable = false)
    private UUID labOrderId;

    @Column(name = "lab_order_item_id")
    private UUID labOrderItemId;

    @Column(name = "accession_number", nullable = false, length = 64)
    private String accessionNumber;

    @Column(name = "barcode_value", nullable = false, length = 128)
    private String barcodeValue;

    @Column(name = "specimen_type", nullable = false, length = 128)
    private String specimenType;

    @Column(name = "container_type", length = 128)
    private String containerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LabSampleStatus status;

    @Column(name = "collected_at")
    private OffsetDateTime collectedAt;

    @Column(name = "collected_by")
    private UUID collectedBy;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "rejected_by")
    private UUID rejectedBy;

    @Column(name = "rejection_reason", length = 128)
    private String rejectionReason;

    @Column(name = "recollection_required", nullable = false)
    private boolean recollectionRequired;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected LabOrderSampleEntity() {
    }

    public static LabOrderSampleEntity create(
            UUID tenantId,
            UUID labOrderId,
            UUID labOrderItemId,
            String accessionNumber,
            String barcodeValue,
            String specimenType,
            String containerType,
            OffsetDateTime collectedAt,
            UUID collectedBy,
            String notes,
            UUID actorAppUserId
    ) {
        OffsetDateTime now = collectedAt == null ? OffsetDateTime.now() : collectedAt;
        LabOrderSampleEntity entity = new LabOrderSampleEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.labOrderId = labOrderId;
        entity.labOrderItemId = labOrderItemId;
        entity.accessionNumber = accessionNumber;
        entity.barcodeValue = barcodeValue;
        entity.specimenType = specimenType;
        entity.containerType = containerType;
        entity.status = LabSampleStatus.COLLECTED;
        entity.collectedAt = now;
        entity.collectedBy = collectedBy;
        entity.notes = notes;
        entity.createdAt = now;
        entity.createdBy = actorAppUserId;
        entity.updatedAt = now;
        entity.updatedBy = actorAppUserId;
        return entity;
    }

    public void markReceived(OffsetDateTime receivedAt, UUID receivedBy, UUID actorAppUserId) {
        this.status = LabSampleStatus.RECEIVED;
        this.receivedAt = receivedAt == null ? OffsetDateTime.now() : receivedAt;
        this.receivedBy = receivedBy;
        this.updatedAt = this.receivedAt;
        this.updatedBy = actorAppUserId;
    }

    public void markRejected(String rejectionReason, boolean recollectionRequired, String notes, UUID actorAppUserId) {
        this.status = recollectionRequired ? LabSampleStatus.RECOLLECTION_REQUIRED : LabSampleStatus.REJECTED;
        this.rejectedAt = OffsetDateTime.now();
        this.rejectedBy = actorAppUserId;
        this.rejectionReason = rejectionReason;
        this.recollectionRequired = recollectionRequired;
        this.notes = notes;
        this.updatedAt = this.rejectedAt;
        this.updatedBy = actorAppUserId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getLabOrderId() { return labOrderId; }
    public UUID getLabOrderItemId() { return labOrderItemId; }
    public String getAccessionNumber() { return accessionNumber; }
    public String getBarcodeValue() { return barcodeValue; }
    public String getSpecimenType() { return specimenType; }
    public String getContainerType() { return containerType; }
    public LabSampleStatus getStatus() { return status; }
    public OffsetDateTime getCollectedAt() { return collectedAt; }
    public UUID getCollectedBy() { return collectedBy; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public UUID getReceivedBy() { return receivedBy; }
    public OffsetDateTime getRejectedAt() { return rejectedAt; }
    public UUID getRejectedBy() { return rejectedBy; }
    public String getRejectionReason() { return rejectionReason; }
    public boolean isRecollectionRequired() { return recollectionRequired; }
    public String getNotes() { return notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
}
