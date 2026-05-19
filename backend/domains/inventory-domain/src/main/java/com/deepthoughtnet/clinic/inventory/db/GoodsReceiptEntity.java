package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "pharmacy_goods_receipts",
        indexes = {
                @Index(name = "ix_pharmacy_goods_receipts_tenant_supplier", columnList = "tenant_id,supplier_id"),
                @Index(name = "ix_pharmacy_goods_receipts_tenant_status", columnList = "tenant_id,matching_status")
        }
)
public class GoodsReceiptEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(name = "supplier_invoice_id")
    private UUID supplierInvoiceId;

    @Column(name = "receipt_number", nullable = false, length = 128)
    private String receiptNumber;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "items_json", nullable = false, columnDefinition = "text")
    private String itemsJson;

    @Column(name = "matching_status", nullable = false, length = 24)
    private String matchingStatus;

    @Column(name = "variance_summary", columnDefinition = "text")
    private String varianceSummary;

    @Column(name = "approval_note", columnDefinition = "text")
    private String approvalNote;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected GoodsReceiptEntity() {
    }

    public static GoodsReceiptEntity create(UUID tenantId, UUID supplierId, UUID purchaseOrderId, UUID supplierInvoiceId, String receiptNumber, OffsetDateTime receivedAt, UUID locationId, String itemsJson, UUID createdBy) {
        OffsetDateTime now = OffsetDateTime.now();
        GoodsReceiptEntity entity = new GoodsReceiptEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.supplierId = supplierId;
        entity.purchaseOrderId = purchaseOrderId;
        entity.supplierInvoiceId = supplierInvoiceId;
        entity.receiptNumber = receiptNumber;
        entity.receivedAt = receivedAt;
        entity.locationId = locationId;
        entity.itemsJson = itemsJson;
        entity.matchingStatus = "PENDING";
        entity.createdBy = createdBy;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void review(String matchingStatus, String varianceSummary, String approvalNote) {
        this.matchingStatus = matchingStatus;
        this.varianceSummary = varianceSummary;
        this.approvalNote = approvalNote;
        this.updatedAt = OffsetDateTime.now();
    }

    public void confirm(UUID confirmedBy) {
        this.confirmedBy = confirmedBy;
        this.confirmedAt = OffsetDateTime.now();
        this.matchingStatus = "CONFIRMED";
        this.updatedAt = this.confirmedAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public UUID getSupplierInvoiceId() { return supplierInvoiceId; }
    public String getReceiptNumber() { return receiptNumber; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public UUID getLocationId() { return locationId; }
    public String getItemsJson() { return itemsJson; }
    public String getMatchingStatus() { return matchingStatus; }
    public String getVarianceSummary() { return varianceSummary; }
    public String getApprovalNote() { return approvalNote; }
    public UUID getConfirmedBy() { return confirmedBy; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
