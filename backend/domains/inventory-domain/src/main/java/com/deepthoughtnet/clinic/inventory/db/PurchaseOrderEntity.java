package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "pharmacy_purchase_orders",
        indexes = {
                @Index(name = "ix_pharmacy_purchase_orders_tenant_supplier", columnList = "tenant_id,supplier_id"),
                @Index(name = "ix_pharmacy_purchase_orders_tenant_status", columnList = "tenant_id,matching_status")
        }
)
public class PurchaseOrderEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "po_number", nullable = false, length = 128)
    private String poNumber;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "items_json", nullable = false, columnDefinition = "text")
    private String itemsJson;

    @Column(name = "matching_status", nullable = false, length = 24)
    private String matchingStatus;

    @Column(name = "variance_summary", columnDefinition = "text")
    private String varianceSummary;

    @Column(name = "approval_note", columnDefinition = "text")
    private String approvalNote;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected PurchaseOrderEntity() {
    }

    public static PurchaseOrderEntity create(UUID tenantId, UUID supplierId, String poNumber, LocalDate orderDate, LocalDate expectedDeliveryDate, String itemsJson, UUID createdBy) {
        OffsetDateTime now = OffsetDateTime.now();
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.supplierId = supplierId;
        entity.poNumber = poNumber;
        entity.orderDate = orderDate;
        entity.expectedDeliveryDate = expectedDeliveryDate;
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

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSupplierId() { return supplierId; }
    public String getPoNumber() { return poNumber; }
    public LocalDate getOrderDate() { return orderDate; }
    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public String getItemsJson() { return itemsJson; }
    public String getMatchingStatus() { return matchingStatus; }
    public String getVarianceSummary() { return varianceSummary; }
    public String getApprovalNote() { return approvalNote; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
