package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_transactions",
        indexes = {
                @Index(name = "ix_inventory_transactions_tenant_medicine", columnList = "tenant_id,medicine_id"),
                @Index(name = "ix_inventory_transactions_tenant_created", columnList = "tenant_id,created_at")
        }
)
public class InventoryTransactionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "medicine_id", nullable = false)
    private UUID medicineId;

    @Column(name = "stock_batch_id")
    private UUID stockBatchId;

    @Column(name = "transaction_type", nullable = false, length = 24)
    private String transactionType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reference_type", length = 64)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected InventoryTransactionEntity() {
    }

    public static InventoryTransactionEntity create(
            UUID tenantId,
            UUID medicineId,
            UUID stockBatchId,
            String transactionType,
            int quantity,
            String referenceType,
            UUID referenceId,
            UUID createdBy,
            String reason,
            String notes
    ) {
        InventoryTransactionEntity entity = new InventoryTransactionEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.medicineId = medicineId;
        entity.stockBatchId = stockBatchId;
        entity.transactionType = transactionType;
        entity.quantity = quantity;
        entity.referenceType = referenceType;
        entity.referenceId = referenceId;
        entity.createdBy = createdBy;
        entity.reason = reason;
        entity.notes = notes;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getMedicineId() { return medicineId; }
    public UUID getStockBatchId() { return stockBatchId; }
    public String getTransactionType() { return transactionType; }
    public int getQuantity() { return quantity; }
    public String getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public UUID getCreatedBy() { return createdBy; }
    public String getReason() { return reason; }
    public String getNotes() { return notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
