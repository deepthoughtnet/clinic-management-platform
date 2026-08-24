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
        name = "lab_specimen_test_links",
        indexes = {
                @Index(name = "ix_lab_specimen_test_links_item", columnList = "tenant_id,lab_order_item_id,active")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lab_specimen_test_link", columnNames = {"tenant_id", "lab_order_sample_id", "lab_order_item_id"})
        }
)
public class LaboratorySpecimenTestLinkEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lab_order_id", nullable = false)
    private UUID labOrderId;

    @Column(name = "lab_order_sample_id", nullable = false)
    private UUID labOrderSampleId;

    @Column(name = "lab_order_item_id", nullable = false)
    private UUID labOrderItemId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sample_accession_number", length = 64)
    private String sampleAccessionNumber;

    @Column(name = "sample_barcode_value", length = 128)
    private String sampleBarcodeValue;

    @Column(name = "sample_specimen_type", length = 128)
    private String sampleSpecimenType;

    @Column(name = "sample_container_type", length = 128)
    private String sampleContainerType;

    @Column(name = "sample_status", length = 32)
    private String sampleStatus;

    @Column(name = "collected_at")
    private OffsetDateTime collectedAt;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt;

    @Column(name = "linked_by")
    private UUID linkedBy;

    @Column(name = "unlinked_at")
    private OffsetDateTime unlinkedAt;

    @Column(name = "unlinked_by")
    private UUID unlinkedBy;

    protected LaboratorySpecimenTestLinkEntity() {
    }

    public static LaboratorySpecimenTestLinkEntity create(
            UUID tenantId,
            UUID labOrderId,
            UUID labOrderSampleId,
            UUID labOrderItemId,
            String sampleAccessionNumber,
            String sampleBarcodeValue,
            String sampleSpecimenType,
            String sampleContainerType,
            String sampleStatus,
            OffsetDateTime collectedAt,
            OffsetDateTime receivedAt,
            UUID actorAppUserId
    ) {
        LaboratorySpecimenTestLinkEntity entity = new LaboratorySpecimenTestLinkEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.labOrderId = labOrderId;
        entity.labOrderSampleId = labOrderSampleId;
        entity.labOrderItemId = labOrderItemId;
        entity.active = true;
        entity.sampleAccessionNumber = sampleAccessionNumber;
        entity.sampleBarcodeValue = sampleBarcodeValue;
        entity.sampleSpecimenType = sampleSpecimenType;
        entity.sampleContainerType = sampleContainerType;
        entity.sampleStatus = sampleStatus;
        entity.collectedAt = collectedAt;
        entity.receivedAt = receivedAt;
        entity.linkedAt = OffsetDateTime.now();
        entity.linkedBy = actorAppUserId;
        return entity;
    }

    public void reactivate(
            String sampleAccessionNumber,
            String sampleBarcodeValue,
            String sampleSpecimenType,
            String sampleContainerType,
            String sampleStatus,
            OffsetDateTime collectedAt,
            OffsetDateTime receivedAt,
            UUID actorAppUserId
    ) {
        this.active = true;
        this.sampleAccessionNumber = sampleAccessionNumber;
        this.sampleBarcodeValue = sampleBarcodeValue;
        this.sampleSpecimenType = sampleSpecimenType;
        this.sampleContainerType = sampleContainerType;
        this.sampleStatus = sampleStatus;
        this.collectedAt = collectedAt;
        this.receivedAt = receivedAt;
        this.linkedAt = OffsetDateTime.now();
        this.linkedBy = actorAppUserId;
        this.unlinkedAt = null;
        this.unlinkedBy = null;
    }

    public void deactivate(UUID actorAppUserId) {
        this.active = false;
        this.unlinkedAt = OffsetDateTime.now();
        this.unlinkedBy = actorAppUserId;
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

    public UUID getLabOrderSampleId() {
        return labOrderSampleId;
    }

    public UUID getLabOrderItemId() {
        return labOrderItemId;
    }

    public boolean isActive() {
        return active;
    }

    public String getSampleAccessionNumber() {
        return sampleAccessionNumber;
    }

    public String getSampleBarcodeValue() {
        return sampleBarcodeValue;
    }

    public String getSampleSpecimenType() {
        return sampleSpecimenType;
    }

    public String getSampleContainerType() {
        return sampleContainerType;
    }

    public String getSampleStatus() {
        return sampleStatus;
    }

    public OffsetDateTime getCollectedAt() {
        return collectedAt;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public OffsetDateTime getLinkedAt() {
        return linkedAt;
    }

    public UUID getLinkedBy() {
        return linkedBy;
    }

    public OffsetDateTime getUnlinkedAt() {
        return unlinkedAt;
    }

    public UUID getUnlinkedBy() {
        return unlinkedBy;
    }
}
